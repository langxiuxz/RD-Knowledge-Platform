package com.studyagent.file.service;

import com.studyagent.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
    private final Path root;

    public LocalFileStorageService(AppProperties properties) throws IOException {
        this.root = Paths.get(properties.storage().localRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public StoredFile store(MultipartFile file) throws IOException {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        String storageKey = UUID.randomUUID() + (extension == null ? "" : "." + extension);
        Path target = root.resolve(storageKey).normalize();
        if (!target.getParent().equals(root)) {
            throw new IOException("Invalid storage key");
        }
        Files.copy(file.getInputStream(), target);
        return new StoredFile(storageKey, originalName, file.getContentType(), file.getSize());
    }

    @Override
    public Resource load(String storageKey) throws IOException {
        Path target = safePath(storageKey);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IOException("Stored file not found");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(safePath(storageKey));
    }

    private Path safePath(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")
                || storageKey.contains("/") || storageKey.contains("\\")) {
            throw new IOException("Invalid storage key");
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.getParent().equals(root)) {
            throw new IOException("Invalid storage key");
        }
        return target;
    }
}
