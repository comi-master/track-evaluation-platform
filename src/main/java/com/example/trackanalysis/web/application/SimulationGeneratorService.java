package com.example.trackanalysis.web.application;

import com.example.trackanalysis.analysis.application.AnalysisApplicationService;
import com.example.trackanalysis.dataset.api.CreateDatasetRequest;
import com.example.trackanalysis.dataset.application.DatasetApplicationService;
import com.example.trackanalysis.track.application.TrackFileApplicationService;
import com.example.trackanalysis.track.domain.TrackSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!no-persistence")
public class SimulationGeneratorService {
  private final DatasetApplicationService datasets;
  private final TrackFileApplicationService files;
  private final AnalysisApplicationService analyses;

  public SimulationGeneratorService(
      DatasetApplicationService datasets,
      TrackFileApplicationService files,
      AnalysisApplicationService analyses) {
    this.datasets = datasets;
    this.files = files;
    this.analyses = analyses;
  }

  public long generate(long userId, SimulationRequest request) {
    request.validate();
    String csv = csv(request);
    String name = "仿真场景 - " + request.motionModel() + " - " + request.points() + "点";
    var dataset = datasets.create(userId, new CreateDatasetRequest(name, request.description()));
    MultipartFile upload = new GeneratedCsvFile(csv.getBytes(StandardCharsets.UTF_8));
    var file = files.upload(userId, dataset.id(), upload, TrackSource.FUSION);
    files.parse(userId, file.id());
    analyses.create(userId, file.id(), request.abnormalThreshold());
    return dataset.id();
  }

  private String csv(SimulationRequest r) {
    StringBuilder out = new StringBuilder("time,true_x,true_y,true_z,track_x,track_y,track_z\n");
    Random random = new Random(r.seed());
    for (int target = 0; target < r.targetCount(); target++) {
      for (int i = 0; i < r.points(); i++) {
        double t = (target * r.points() + i) * r.timeStep();
        double[] truth = position(r.motionModel(), t, target);
        double x = truth[0] + gaussian(random, r.noise());
        double y = r.dimensions() == 2 ? 0 : truth[1] + gaussian(random, r.noise());
        double z = r.dimensions() == 2 ? 0 : truth[2] + gaussian(random, r.noise());
        out.append(format(t))
            .append(',')
            .append(format(truth[0]))
            .append(',')
            .append(format(truth[1]))
            .append(',')
            .append(format(truth[2]))
            .append(',')
            .append(format(x))
            .append(',')
            .append(format(y))
            .append(',')
            .append(format(z))
            .append('\n');
      }
    }
    return out.toString();
  }

  private double[] position(String model, double t, int target) {
    double offset = target * 25.0;
    return switch (model.toUpperCase(Locale.ROOT)) {
      case "CONSTANT_ACCELERATION" ->
          new double[] {offset + 0.5 * 0.08 * t * t, 0.4 * t, Math.sin(t / 8.0)};
      case "CIRCULAR" ->
          new double[] {
            offset + 30 * Math.cos(t / 12.0), 30 * Math.sin(t / 12.0), Math.sin(t / 6.0)
          };
      default -> new double[] {offset + 1.2 * t, 0.4 * t, Math.sin(t / 8.0)};
    };
  }

  private double gaussian(Random random, double standardDeviation) {
    return standardDeviation == 0 ? 0 : random.nextGaussian() * standardDeviation;
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.6f", value);
  }

  public record SimulationRequest(
      String motionModel,
      int dimensions,
      double noise,
      int targetCount,
      int points,
      double timeStep,
      long seed,
      double abnormalThreshold,
      String description) {
    public void validate() {
      if (!motionModel.equals("CONSTANT_VELOCITY")
          && !motionModel.equals("CONSTANT_ACCELERATION")
          && !motionModel.equals("CIRCULAR")) throw new IllegalArgumentException("不支持的运动模型");
      if (dimensions != 2 && dimensions != 3) throw new IllegalArgumentException("维度只能是 2 或 3");
      if (!Double.isFinite(noise) || noise < 0 || noise > 100)
        throw new IllegalArgumentException("噪声必须在 0 到 100 之间");
      if (targetCount < 1 || targetCount > 10 || points < 10 || points > 10000)
        throw new IllegalArgumentException("目标数或点数超出范围");
      if (!Double.isFinite(timeStep) || timeStep <= 0 || timeStep > 100)
        throw new IllegalArgumentException("采样间隔无效");
      if (!Double.isFinite(abnormalThreshold) || abnormalThreshold < 0 || abnormalThreshold > 1000)
        throw new IllegalArgumentException("异常阈值无效");
    }
  }

  private record GeneratedCsvFile(byte[] bytes) implements MultipartFile {
    public String getName() {
      return "file";
    }

    public String getOriginalFilename() {
      return "simulation.csv";
    }

    public String getContentType() {
      return "text/csv";
    }

    public boolean isEmpty() {
      return bytes.length == 0;
    }

    public long getSize() {
      return bytes.length;
    }

    public byte[] getBytes() {
      return bytes.clone();
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(bytes);
    }

    public void transferTo(java.io.File dest) throws IOException {
      java.nio.file.Files.write(dest.toPath(), bytes);
    }

    public void transferTo(java.nio.file.Path dest) throws IOException {
      java.nio.file.Files.write(dest, bytes);
    }
  }
}
