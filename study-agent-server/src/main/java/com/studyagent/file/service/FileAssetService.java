package com.studyagent.file.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.file.dto.FileUploadRequest;
import com.studyagent.file.entity.FileAssetEntity;
import com.studyagent.file.mapper.FileAssetMapper;
import com.studyagent.file.vo.FileUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FileAssetService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");
    private final FileStorageService storage;
    private final FileAssetMapper mapper;

    public FileAssetService(FileStorageService storage, FileAssetMapper mapper) {
        this.storage = storage;
        this.mapper = mapper;
    }

    @Transactional
    public FileUploadVO upload(Long userId, String type, MultipartFile file) {
        validate(type, file);
        FileStorageService.StoredFile stored;
        try {
            stored = storage.store(file);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        String url = "/api/files/" + stored.storageKey();
        FileAssetEntity asset = new FileAssetEntity();
        asset.setUserId(userId);
        asset.setType(type);
        asset.setOriginalName(stored.originalName());
        asset.setStorageKey(stored.storageKey());
        asset.setUrl(url);
        asset.setMimeType(MIME_BY_EXTENSION.get(extension(stored.originalName())));
        asset.setFileSize(stored.size());
        try {
            mapper.insert(asset);
        } catch (RuntimeException exception) {
            try { storage.delete(stored.storageKey()); } catch (IOException ignored) { }
            throw exception;
        }
        return new FileUploadVO(url, stored.originalName());
    }

    public DownloadedFile load(Long userId, String storageKey) {
        FileAssetEntity asset = mapper.findByStorageKeyAndUser(storageKey, userId);
        if (asset == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        try {
            return new DownloadedFile(storage.load(asset.getStorageKey()), asset.getMimeType());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    private void validate(String type, MultipartFile file) {
        if (!Set.of("AVATAR", "IMAGE").contains(type)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "type只能是AVATAR或IMAGE");
        }
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.FILE_INVALID, "文件不能为空");
        if (file.getSize() > MAX_BYTES) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank() || original.length() > 255 || original.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "文件名不合法");
        }
        String ext = extension(original);
        String expectedMime = MIME_BY_EXTENSION.get(ext);
        if (expectedMime == null || !expectedMime.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "仅支持PNG/JPEG/GIF/WebP图片且MIME必须匹配");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            boolean valid = switch (ext) {
                case "png" -> starts(head, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
                case "jpg", "jpeg" -> starts(head, new int[]{0xff, 0xd8, 0xff});
                case "gif" -> startsAscii(head, "GIF8");
                case "webp" -> startsAscii(head, "RIFF") && asciiAt(head, 8, "WEBP");
                default -> false;
            };
            if (!valid) throw new BusinessException(ErrorCode.FILE_INVALID, "文件内容与图片类型不匹配");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取上传文件");
        }
    }

    private String extension(String fileName) {
        String clean = StringUtils.cleanPath(fileName.replace('\\', '/'));
        String ext = StringUtils.getFilenameExtension(clean);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    private boolean starts(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 0xff) != signature[i]) return false;
        return true;
    }
    private boolean startsAscii(byte[] bytes, String signature) { return asciiAt(bytes, 0, signature); }
    private boolean asciiAt(byte[] bytes, int offset, String signature) {
        if (bytes.length < offset + signature.length()) return false;
        for (int i = 0; i < signature.length(); i++) if (bytes[offset + i] != (byte) signature.charAt(i)) return false;
        return true;
    }

    public record DownloadedFile(Resource resource, String mimeType) { }
}

