package com.codearena.worker.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

final class TarArchives {

    private static final int READ_ONLY_FILE_MODE = 0100444;

    private TarArchives() {
    }

    static byte[] pack(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(requireSafeName(file.getKey()));
                entry.setSize(file.getValue().length);
                entry.setMode(READ_ONLY_FILE_MODE);
                entry.setIds(0, 0);
                entry.setNames("root", "root");
                tar.putArchiveEntry(entry);
                tar.write(file.getValue());
                tar.closeArchiveEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to build tar archive", ex);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads regular files from a Docker archive stream, dropping the leading directory that Docker adds
     * (e.g. {@code out/Main.class} becomes {@code Main.class}).
     */
    static Map<String, byte[]> unpack(InputStream in, int maxTotalBytes) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isFile()) {
                    continue;
                }
                String name = entry.getName();
                int slash = name.indexOf('/');
                String relative = slash >= 0 ? name.substring(slash + 1) : name;
                total += entry.getSize();
                if (total > maxTotalBytes) {
                    throw new IllegalStateException("Archive exceeds " + maxTotalBytes + " bytes");
                }
                files.put(requireSafeName(relative), tar.readAllBytes());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read tar archive", ex);
        }
        return files;
    }

    private static String requireSafeName(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("..")) {
            throw new IllegalArgumentException("Unsafe archive entry name: " + name);
        }
        return name;
    }
}
