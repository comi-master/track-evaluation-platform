package com.example.trackanalysis.web.application;

import com.example.trackanalysis.track.infrastructure.persistence.TrackFileMapper;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointDO;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!no-persistence")
public class TrajectoryQualityMetricService {
  private final TrackFileMapper files;
  private final TrackPointMapper points;

  public TrajectoryQualityMetricService(TrackFileMapper files, TrackPointMapper points) {
    this.files = files;
    this.points = points;
  }

  public ExtendedMetrics metrics(long userId, long fileId) {
    if (files.selectOwnedById(fileId, userId) == null) {
      return new ExtendedMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
    long previous = -1;
    TrackPointDO last = null;
    long count = 0;
    long velocityCount = 0;
    long transitions = 0;
    long stable = 0;
    double errorSquared2d = 0;
    double errorSquaredX = 0;
    double errorSquaredY = 0;
    double errorSquaredZ = 0;
    double velocityErrorSquared = 0;
    double trueLength = 0;
    double trackLength = 0;
    double endpointError = 0;
    while (true) {
      var batch = points.selectAfterSequence(fileId, previous, 1000);
      if (batch.isEmpty()) break;
      for (TrackPointDO current : batch) {
        double errorX = current.getTrackX() - current.getTrueX();
        double errorY = current.getTrackY() - current.getTrueY();
        double errorZ = current.getTrackZ() - current.getTrueZ();
        errorSquared2d += errorX * errorX + errorY * errorY;
        errorSquaredX += errorX * errorX;
        errorSquaredY += errorY * errorY;
        errorSquaredZ += errorZ * errorZ;
        endpointError = Math.sqrt(errorX * errorX + errorY * errorY + errorZ * errorZ);
        count++;
        if (last != null) {
          transitions++;
          double trueStep =
              distance(
                  last.getTrueX(),
                  last.getTrueY(),
                  last.getTrueZ(),
                  current.getTrueX(),
                  current.getTrueY(),
                  current.getTrueZ());
          double trackStep =
              distance(
                  last.getTrackX(),
                  last.getTrackY(),
                  last.getTrackZ(),
                  current.getTrackX(),
                  current.getTrackY(),
                  current.getTrackZ());
          if ((trueStep < 1e-9 && trackStep < 1e-9)
              || (trueStep >= 1e-9 && trackStep <= trueStep * 3.0)) stable++;
          trueLength += trueStep;
          trackLength += trackStep;
          double dt = current.getTimeValue() - last.getTimeValue();
          if (dt > 0) {
            double vx =
                (current.getTrackX() - last.getTrackX()) / dt
                    - (current.getTrueX() - last.getTrueX()) / dt;
            double vy =
                (current.getTrackY() - last.getTrackY()) / dt
                    - (current.getTrueY() - last.getTrueY()) / dt;
            double vz =
                (current.getTrackZ() - last.getTrackZ()) / dt
                    - (current.getTrueZ() - last.getTrueZ()) / dt;
            velocityErrorSquared += vx * vx + vy * vy + vz * vz;
            velocityCount++;
          }
        }
        last = current;
        previous = current.getSequenceNo();
      }
    }
    double continuity = transitions == 0 ? 1.0 : (double) stable / transitions;
    return new ExtendedMetrics(
        count,
        root(errorSquared2d, count),
        root(errorSquaredX, count),
        root(errorSquaredY, count),
        root(errorSquaredZ, count),
        endpointError,
        root(velocityErrorSquared, velocityCount),
        trueLength,
        trackLength,
        continuity,
        count == 0 ? 0 : 1.0);
  }

  public record ExtendedMetrics(
      long pointCount,
      double rmse2d,
      double rmseX,
      double rmseY,
      double rmseZ,
      double endpointError,
      double velocityRmse,
      double trueLength,
      double trackLength,
      double continuity,
      double coverage) {}

  private double root(double value, long count) {
    return count == 0 ? 0 : Math.sqrt(value / count);
  }

  private double distance(Double ax, Double ay, Double az, Double bx, Double by, Double bz) {
    double x = bx - ax, y = by - ay, z = bz - az;
    return Math.sqrt(x * x + y * y + z * z);
  }
}
