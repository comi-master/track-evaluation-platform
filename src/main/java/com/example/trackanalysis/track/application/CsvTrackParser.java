package com.example.trackanalysis.track.application;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.common.exception.ErrorCode;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointDO;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class CsvTrackParser {

  private static final List<String> HEADER =
      List.of("time", "true_x", "true_y", "true_z", "track_x", "track_y", "track_z");
  private final TrackFileProperties properties;
  private final Clock clock;

  public CsvTrackParser(TrackFileProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public long parse(Path path, long fileId, Consumer<List<TrackPointDO>> batchConsumer) {
    var decoder = StandardCharsets.UTF_8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPORT);
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false)
            .get();
    long currentLine = 1;
    try (BufferedReader reader =
            new BufferedReader(new java.io.InputStreamReader(Files.newInputStream(path), decoder));
        CSVParser parser = format.parse(reader)) {
      validateHeader(parser.getHeaderNames());
      List<TrackPointDO> batch = new ArrayList<>(properties.batchSize());
      long sequence = 0;
      double previousTime = 0;
      for (CSVRecord record : parser) {
        long line = Math.max(1, parser.getCurrentLineNumber());
        currentLine = line;
        sequence++;
        if (sequence > properties.maxRows()) {
          throw formatError(line, "maximum row count exceeded");
        }
        if (record.size() != HEADER.size()) {
          throw formatError(line, "expected exactly 7 columns");
        }
        double[] values = new double[HEADER.size()];
        for (int column = 0; column < HEADER.size(); column++) {
          values[column] = parseNumber(record.get(column), line, HEADER.get(column));
        }
        if (sequence > 1 && values[0] <= previousTime) {
          throw formatError(line, "time must be strictly increasing");
        }
        previousTime = values[0];
        batch.add(point(fileId, sequence, values));
        if (batch.size() == properties.batchSize()) {
          batchConsumer.accept(List.copyOf(batch));
          batch.clear();
        }
      }
      if (sequence == 0) {
        throw formatError(1, "file must contain at least one data row");
      }
      if (!batch.isEmpty()) {
        batchConsumer.accept(List.copyOf(batch));
      }
      return sequence;
    } catch (BusinessException exception) {
      throw exception;
    } catch (IOException | IllegalArgumentException exception) {
      long line = Math.max(1, currentLine);
      throw new BusinessException(
          ErrorCode.FILE_FORMAT_ERROR,
          "CSV line " + line + ": CSV syntax or UTF-8 is invalid",
          exception);
    }
  }

  private void validateHeader(List<String> actualHeader) {
    List<String> normalized = new ArrayList<>(actualHeader);
    if (!normalized.isEmpty() && normalized.get(0).startsWith("\uFEFF")) {
      normalized.set(0, normalized.get(0).substring(1));
    }
    if (!HEADER.equals(normalized)) {
      throw formatError(1, "header must be time,true_x,true_y,true_z,track_x,track_y,track_z");
    }
  }

  private double parseNumber(String raw, long line, String column) {
    if (raw == null || raw.isBlank()) {
      throw formatError(line, column + " must not be empty");
    }
    try {
      double value = Double.parseDouble(raw.trim());
      if (!Double.isFinite(value)) {
        throw formatError(line, column + " must be finite");
      }
      return value;
    } catch (NumberFormatException exception) {
      throw formatError(line, column + " must be numeric");
    }
  }

  private TrackPointDO point(long fileId, long sequence, double[] values) {
    TrackPointDO point = new TrackPointDO();
    point.setTrackFileId(fileId);
    point.setSequenceNo(sequence);
    point.setTimeValue(values[0]);
    point.setTrueX(values[1]);
    point.setTrueY(values[2]);
    point.setTrueZ(values[3]);
    point.setTrackX(values[4]);
    point.setTrackY(values[5]);
    point.setTrackZ(values[6]);
    point.setCreatedAt(LocalDateTime.now(clock));
    return point;
  }

  private BusinessException formatError(long line, String reason) {
    return new BusinessException(ErrorCode.FILE_FORMAT_ERROR, "CSV line " + line + ": " + reason);
  }
}
