package com.rvi.diagnostics;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.status.Status;
import com.rvi.domain.ICardStatus;
import com.rvi.domain.ILinkValidation;
import com.rvi.presentation.StudioController;
import com.rvi.service.IReaderService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.TerminalFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Checks packaged resources and native dependencies without connecting to a card. */
public final class NativeBuildVerifier
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeBuildVerifier.class);

    private NativeBuildVerifier()
    {
    }

    public static int run(final String[] args)
    {
        if (args.length != 2)
        {
            LOGGER.error("Usage: SocialCard --verify-native <report-file>");
            return 2;
        }

        final StringBuilder report = new StringBuilder();
        int exitCode = 0;
        try
        {
            report.append("Java VM: ").append(System.getProperty("java.vm.name")).append('\n');
            // Request PC/SC explicitly: the default factory can silently fall back to None.
            final TerminalFactory factory = TerminalFactory.getInstance("PC/SC", null);
            if (!"PC/SC".equals(TerminalFactory.getDefault().getType()))
            {
                throw new IllegalStateException("The default terminal factory is not PC/SC.");
            }
            final int readerCount = factory.terminals().list().size();
            report.append("PC/SC provider: ").append(factory.getProvider().getName()).append('\n');
            report.append("Available readers: ").append(readerCount).append('\n');

            final CompletableFuture<Void> presentation = new CompletableFuture<>();
            Platform.startup(() ->
            {
                Platform.setImplicitExit(false);
                try (StudioController controller = new StudioController(new VerificationReaderService()))
                {
                    final FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                            NativeBuildVerifier.class.getResource("/presentation/studio.fxml")));
                    loader.setControllerFactory(type ->
                    {
                        if (type != StudioController.class)
                        {
                            throw new IllegalArgumentException("Unexpected FXML controller: " + type.getName());
                        }
                        return controller;
                    });
                    final Scene scene = new Scene(loader.load(), 1080, 730);
                    scene.getStylesheets().add(Objects.requireNonNull(
                            NativeBuildVerifier.class.getResource("/presentation/studio.css")).toExternalForm());
                    scene.snapshot(null);
                    presentation.complete(null);
                }
                catch (Exception | LinkageError failure)
                {
                    presentation.completeExceptionally(failure);
                }
            });
            presentation.get(30, TimeUnit.SECONDS);
            report.append("FXML, CSS and JavaFX rendering: PASS\n");
            verifyLogging();
            report.append("File logging: PASS\n");
            report.append("Card connections and writes: none\n");
            report.append("Result: PASS\n");
            LOGGER.info("Native build verification passed with {} available readers", readerCount);
        }
        catch (Exception | LinkageError failure)
        {
            if (failure instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            report.append("Result: FAIL\n").append(failure).append('\n');
            LOGGER.error("Native build verification failed", failure);
            exitCode = 1;
        }
        finally
        {
            Platform.exit();
        }

        try
        {
            Files.writeString(Path.of(args[1]), report, StandardCharsets.UTF_8);
        }
        catch (Exception failure)
        {
            LOGGER.error("Could not save the native build verification report", failure);
            return 1;
        }
        return exitCode;
    }

    private static void verifyLogging() throws IOException
    {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context))
        {
            throw new IllegalStateException("Logback is not the active logging backend.");
        }
        if (context.getStatusManager().getCopyOfStatusList().stream()
                .anyMatch(status -> status.getLevel() == Status.ERROR))
        {
            throw new IllegalStateException("Logback reported a configuration or appender error.");
        }
        if (!(context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE")
                instanceof FileAppender<?> appender) || !appender.isStarted())
        {
            throw new IllegalStateException("The file logger is not running.");
        }
        final String marker = "Native verification marker " + Long.toUnsignedString(System.nanoTime());
        LOGGER.info("{}", marker);
        try (var lines = Files.lines(Path.of(appender.getFile()), StandardCharsets.UTF_8))
        {
            if (lines.noneMatch(line -> line.contains(marker)))
            {
                throw new IllegalStateException("The verification message did not reach the log file.");
            }
        }
    }

    private static final class VerificationReaderService implements IReaderService
    {
        @Override
        public List<String> readers()
        {
            return List.of();
        }

        @Override
        public ICardStatus status(final String reader)
        {
            return new ICardStatus.NoCard();
        }

        @Override
        public ILinkValidation validateLinks(final List<String> urls)
        {
            return new ILinkValidation.Invalid("Verification mode");
        }

        @Override
        public List<String> read(final String reader)
        {
            throw new UnsupportedOperationException("Verification does not read cards.");
        }

        @Override
        public void write(final String reader, final List<String> urls)
        {
            throw new UnsupportedOperationException("Verification does not write cards.");
        }
    }
}
