package com.example.trackanalysis.track.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.dataset.infrastructure.persistence.DatasetMapper;
import com.example.trackanalysis.storage.ObjectStorageService;
import com.example.trackanalysis.track.api.TrackFilePageResponse;
import com.example.trackanalysis.track.api.TrackFileResponse;
import com.example.trackanalysis.track.api.TrackPointPageResponse;
import com.example.trackanalysis.track.api.TrackPointResponse;
import com.example.trackanalysis.track.domain.ParseStatus;
import com.example.trackanalysis.track.domain.TrackSource;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TrackFileApplicationService {

  private static final Logger log = LoggerFactory.getLogger(TrackFileApplicationService.class);

  private final DatasetMapper datasetMapper;
  private final TrackFileMapper fileMapper;
  private final TrackPointMapper pointMapper;
  private final ObjectStorageService storage;
  private final CsvTrackParser parser;
  private final TrackFileProperties properties;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public TrackFileApplicationService(
      DatasetMapper datasetMapper,
      TrackFileMapper fileMapper,
      TrackPointMapper pointMapper,
      ObjectStorageService storage,
      CsvTrackParser parser,
      TrackFileProperties properties,
      TransactionTemplate transactions,
      Clock clock) {
    this.datasetMapper = datasetMapper;
    this.fileMapper = fileMapper;
    this.pointMapper = pointMapper;
    this.storage = storage;
    this.parser = parser;
    this.properties = properties;
    this.transactions = transactions;
    this.clock = clock;
  }

  public TrackFileResponse upload(
      long userId, long datasetId, MultipartFile upload, TrackSource source) {
    requireOwnedDataset(userId, datasetId);
    String originalName = safeFileName(upload.getOriginalFilename());
    if (upload.isEmpty()) {
      throw invalid("CSV file must not be empty");
    }
    if (upload.getSize() > properties.maxSizeBytes()) {
      throw invalid("CSV file exceeds the configured size limit");
    }
    Path temporary = null;
    String objectName = userId + "/" + datasetId + "/" + UUID.randomUUID() + ".csv";
    boolean uploaded = false;
    try {
      temporary = Files.createTempFile("track-upload-", ".csv");
      String sha256 = copyAndHash(upload, temporary);
      long size = Files.size(temporary);
      try (InputStream input = Files.newInputStream(temporary)) {
        storage.put(objectName, input, size, "text/csv");
      }
      uploaded = true;
      TrackFileDO file = new TrackFileDO();
      file.setDatasetId(datasetId);
      file.setOriginalName(originalName);
      file.setObjectName(objectName);
      file.setSha256(sha256);
      file.setFileSize(size);
      file.setTrackSource(source);
      file.setParseStatus(ParseStatus.UPLOADED);
      file.setPointCount(0L);
      file.setVersion(0);
      LocalDateTime now = LocalDateTime.now(clock);
      file.setCreatedAt(now);
      file.setUpdatedAt(now);
      try {
        if (fileMapper.insertOwned(file, userId) != 1) {
          throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
        }
      } catch (DuplicateKeyException exception) {
        throw new BusinessException(
            ErrorCode.CONFLICT, "Identical file content already exists in this dataset", exception);
      }
      return toResponse(file);
    } catch (BusinessException exception) {
      if (uploaded) storage.deleteBestEffort(objectName);
      throw exception;
    } catch (RuntimeException exception) {
      if (uploaded) storage.deleteBestEffort(objectName);
      throw new BusinessException(
          ErrorCode.INFRASTRUCTURE_ERROR, "Track file metadata storage failed", exception);
    } catch (IOException exception) {
      if (uploaded) storage.deleteBestEffort(objectName);
      throw new BusinessException(
          ErrorCode.INFRASTRUCTURE_ERROR, "Temporary file processing failed", exception);
    } finally {
      deleteTemporary(temporary);
    }
  }

  public TrackFileResponse parse(long userId, long fileId) {
    TrackFileDO owned = fileMapper.selectOwnedById(fileId, userId);
    if (owned == null) throw fileNotFound();
    int claimed =
        transactions.execute(
            status -> fileMapper.claimForParsing(fileId, userId, LocalDateTime.now(clock)));
    if (claimed == 0)
      throw new BusinessException(
          ErrorCode.CONFLICT, "Track file cannot be parsed in its current state");
    Path temporary = null;
    try {
      temporary = Files.createTempFile("track-parse-", ".csv");
      try (InputStream input = storage.get(owned.getObjectName())) {
        copyBounded(input, temporary, owned.getFileSize());
      }
      Path parsePath = temporary;
      transactions.executeWithoutResult(
          status -> {
            pointMapper.deleteByFileId(fileId);
            long count = parser.parse(parsePath, fileId, pointMapper::batchInsert);
            if (fileMapper.markParsed(fileId, count, LocalDateTime.now(clock)) != 1) {
              throw new IllegalStateException("Track file parsing state changed unexpectedly");
            }
          });
      return get(userId, fileId);
    } catch (RuntimeException | IOException exception) {
      String safeError = safeParseError(exception);
      transactions.executeWithoutResult(
          status -> fileMapper.markFailed(fileId, safeError, LocalDateTime.now(clock)));
      if (exception instanceof BusinessException businessException) throw businessException;
      throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR, safeError, exception);
    } finally {
      deleteTemporary(temporary);
    }
  }

  public TrackFileResponse get(long userId, long fileId) {
    TrackFileDO file = fileMapper.selectOwnedById(fileId, userId);
    if (file == null) throw fileNotFound();
    return toResponse(file);
  }

  public long visibleOwnerId(long actorId, boolean administrator, long fileId) {
    Long owner = fileMapper.selectVisibleOwnerId(fileId, actorId, administrator);
    if (owner == null) throw fileNotFound();
    return owner;
  }

  public TrackFileDownload download(long userId, long fileId) {
    TrackFileDO file = fileMapper.selectOwnedById(fileId, userId);
    if (file == null) throw fileNotFound();
    return new TrackFileDownload(
        file.getOriginalName(), file.getFileSize(), storage.get(file.getObjectName()));
  }

  public void deleteDatasetFiles(long datasetId) {
    if (fileMapper.countNonTerminalTasks(datasetId) > 0) {
      throw new BusinessException(ErrorCode.CONFLICT, "Dataset has a pending or running task");
    }
    var datasetFiles = fileMapper.selectActiveDatasetFiles(datasetId);
    if (datasetFiles.size() > 1) {
      throw new BusinessException(
          ErrorCode.CONFLICT,
          "Datasets with multiple stored files require the managed cleanup workflow");
    }
    for (TrackFileDO file : datasetFiles) {
      storage.delete(file.getObjectName());
    }
  }

  public TrackFilePageResponse list(
      long userId, long datasetId, int page, int size, TrackSource source, ParseStatus status) {
    requireOwnedDataset(userId, datasetId);
    IPage<TrackFileDO> result =
        fileMapper.selectOwnedPage(new Page<>(page, size), datasetId, userId, source, status);
    return new TrackFilePageResponse(
        result.getCurrent(),
        result.getSize(),
        result.getTotal(),
        result.getPages(),
        result.getRecords().stream().map(this::toResponse).toList());
  }

  public TrackPointPageResponse points(long userId, long fileId, int page, int size) {
    if (fileMapper.selectOwnedById(fileId, userId) == null) throw fileNotFound();
    IPage<TrackPointDO> result =
        pointMapper.selectOwnedPage(new Page<>(page, size), fileId, userId);
    return new TrackPointPageResponse(
        result.getCurrent(),
        result.getSize(),
        result.getTotal(),
        result.getPages(),
        result.getRecords().stream().map(this::toPointResponse).toList());
  }

  private String copyAndHash(MultipartFile upload, Path target) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
    long count = 0;
    byte[] buffer = new byte[8192];
    try (InputStream raw = upload.getInputStream();
        DigestInputStream input = new DigestInputStream(raw, digest);
        var output = Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING)) {
      int read;
      while ((read = input.read(buffer)) != -1) {
        count += read;
        if (count > properties.maxSizeBytes())
          throw invalid("CSV file exceeds the configured size limit");
        output.write(buffer, 0, read);
      }
    }
    if (count == 0) throw invalid("CSV file must not be empty");
    return HexFormat.of().formatHex(digest.digest());
  }

  private void copyBounded(InputStream input, Path target, long expectedSize) throws IOException {
    long limit = Math.min(properties.maxSizeBytes(), expectedSize);
    long count = 0;
    byte[] buffer = new byte[8192];
    try (var output = Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING)) {
      int read;
      while ((read = input.read(buffer)) != -1) {
        count += read;
        if (count > limit) throw new IOException("Stored object exceeds recorded size");
        output.write(buffer, 0, read);
      }
    }
    if (count != expectedSize) throw new IOException("Stored object size does not match metadata");
  }

  private String safeFileName(String raw) {
    String normalized = raw == null ? "" : raw.replace('\\', '/');
    normalized =
        normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
    if (normalized.isEmpty()
        || normalized.length() > 255
        || !normalized.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw invalid("File name must be a safe .csv name of at most 255 characters");
    }
    return normalized;
  }

  private void requireOwnedDataset(long userId, long datasetId) {
    if (datasetMapper.countOwnedActive(datasetId, userId) == 0) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset was not found");
    }
  }

  private TrackFileResponse toResponse(TrackFileDO file) {
    return new TrackFileResponse(
        file.getId(),
        file.getDatasetId(),
        file.getOriginalName(),
        file.getSha256(),
        file.getFileSize(),
        file.getTrackSource(),
        file.getParseStatus(),
        file.getPointCount(),
        file.getParseError(),
        file.getCreatedAt());
  }

  private TrackPointResponse toPointResponse(TrackPointDO point) {
    return new TrackPointResponse(
        point.getSequenceNo(),
        point.getTimeValue(),
        point.getTrueX(),
        point.getTrueY(),
        point.getTrueZ(),
        point.getTrackX(),
        point.getTrackY(),
        point.getTrackZ());
  }

  private String safeParseError(Exception exception) {
    String message =
        exception instanceof BusinessException
            ? exception.getMessage()
            : "Track file parsing failed";
    if (message == null || message.isBlank()) message = "Track file parsing failed";
    return message.substring(0, Math.min(500, message.length()));
  }

  private void deleteTemporary(Path path) {
    if (path == null) return;
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      log.warn("Sensitive track temporary file cleanup failed");
      log.debug("Temporary file cleanup failure detail", exception);
    }
  }

  private BusinessException invalid(String message) {
    return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
  }

  private BusinessException fileNotFound() {
    return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Track file was not found");
  }
}
