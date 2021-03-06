package io.fstream.simulate.output;

import io.fstream.core.model.event.Event;
import io.fstream.core.model.event.EventType;
import io.fstream.core.util.Codec;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.PreDestroy;

import lombok.SneakyThrows;
import lombok.val;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("file")
public class FileOutput implements Output, Closeable {

  private final PrintWriter tradeWriter;
  private final PrintWriter orderWriter;
  private final PrintWriter quoteWriter;
  private final PrintWriter snapshotWriter;

  @SneakyThrows
  @Autowired
  public FileOutput(@Value("${simulate.file.dir}") File outputDir) {
    this.tradeWriter = new PrintWriter(new File(outputDir, "fstream-simulate-trades.json"));
    this.orderWriter = new PrintWriter(new File(outputDir, "fstream-simulate-orders.json"));
    this.quoteWriter = new PrintWriter(new File(outputDir, "fstream-simulate-quotes.json"));
    this.snapshotWriter = new PrintWriter(new File(outputDir, "fstream-simulate-snapshots.json"));
  }

  @Override
  @SneakyThrows
  public void write(Event event) {
    val writer = getWriter(event);
    writer.println(Codec.encodeText(event));
  }

  private PrintWriter getWriter(Event event) {
    if (event.getType() == EventType.TRADE) {
      return tradeWriter;
    } else if (event.getType() == EventType.ORDER) {
      return orderWriter;
    } else if (event.getType() == EventType.QUOTE) {
      return quoteWriter;
    } else if (event.getType() == EventType.SNAPSHOT) {
      return snapshotWriter;
    }

    throw new IllegalStateException();
  }

  @Override
  @PreDestroy
  public void close() throws IOException {
    tradeWriter.close();
    orderWriter.close();
    quoteWriter.close();
  }

}
