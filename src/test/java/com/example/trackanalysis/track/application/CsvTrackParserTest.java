package com.example.trackanalysis.track.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.trackanalysis.common.exception.BusinessException;
import com.example.trackanalysis.track.infrastructure.persistence.TrackPointDO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTrackParserTest {

  private static final String HEADER = "time,true_x,true_y,true_z,track_x,track_y,track_z\n";
  @TempDir Path temporaryDirectory;
  private final CsvTrackParser parser =
      new CsvTrackParser(
          new TrackFileProperties(1024, 2, 2),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void parsesBomAndEmitsConfiguredBatches() throws IOException {
    Path csv = write("\uFEFF" + HEADER + "1,2,3,4,5,6,7\n2,3,4,5,6,7,8\n");
    List<List<TrackPointDO>> batches = new ArrayList<>();

    long count = parser.parse(csv, 42, batches::add);

    assertThat(count).isEqualTo(2);
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).extracting(TrackPointDO::getSequenceNo).containsExactly(1L, 2L);
    assertThat(batches.get(0).get(0).getTrackFileId()).isEqualTo(42);
  }

  @Test
  void supportsQuotedNumericValuesThroughCommonsCsv() throws IOException {
    Path csv = write(HEADER + "\"1\",2,3,4,5,6,7\n");
    List<TrackPointDO> points = new ArrayList<>();

    parser.parse(csv, 1, points::addAll);

    assertThat(points).singleElement().extracting(TrackPointDO::getTimeValue).isEqualTo(1.0);
  }

  @Test
  void rejectsEmptyFileAndHeaderOnlyFile() throws IOException {
    assertFailure(write(""), "line 1");
    assertFailure(write(HEADER), "at least one data row");
  }

  @Test
  void rejectsWrongHeaderOrder() throws IOException {
    assertFailure(
        write("true_x,time,true_y,true_z,track_x,track_y,track_z\n1,2,3,4,5,6,7\n"),
        "header must be");
  }

  @Test
  void rejectsWrongColumnCountAndEmptyValueWithSafeLine() throws IOException {
    assertFailure(write(HEADER + "1,2,3\n"), "CSV line 2");
    assertFailure(write(HEADER + "1,2,,4,5,6,7\n"), "CSV line 2: true_y must not be empty");
  }

  @Test
  void rejectsNonNumericAndNonFiniteNumbers() throws IOException {
    assertFailure(write(HEADER + "text,2,3,4,5,6,7\n"), "time must be numeric");
    assertFailure(write(HEADER + "NaN,2,3,4,5,6,7\n"), "time must be finite");
    assertFailure(write(HEADER + "Infinity,2,3,4,5,6,7\n"), "time must be finite");
  }

  @Test
  void requiresStrictlyIncreasingTime() throws IOException {
    assertFailure(write(HEADER + "1,2,3,4,5,6,7\n1,3,4,5,6,7,8\n"), "strictly increasing");
  }

  @Test
  void enforcesMaximumRows() throws IOException {
    assertFailure(
        write(HEADER + "1,2,3,4,5,6,7\n2,3,4,5,6,7,8\n3,4,5,6,7,8,9\n"), "maximum row count");
  }

  @Test
  void rejectsMalformedUtf8() throws IOException {
    Path file = temporaryDirectory.resolve("malformed.csv");
    Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
    assertFailure(file, "CSV syntax or UTF-8 is invalid");
  }

  @Test
  void reportsPhysicalLineForMultilineQuotedField() throws IOException {
    Path csv = write(HEADER + "\"1\n2\",2,3,4,5,6,7\n");

    assertFailure(csv, "CSV line 3: time must be numeric");
  }

  private Path write(String value) throws IOException {
    Path file = Files.createTempFile(temporaryDirectory, "track-", ".csv");
    Files.writeString(file, value, StandardCharsets.UTF_8);
    return file;
  }

  private void assertFailure(Path file, String message) {
    assertThatThrownBy(() -> parser.parse(file, 1, ignored -> {}))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining(message)
        .hasMessageNotContaining("1,2,3,4");
  }
}
