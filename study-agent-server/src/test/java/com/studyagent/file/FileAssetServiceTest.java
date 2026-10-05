package com.studyagent.file;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.file.mapper.FileAssetMapper;
import com.studyagent.file.service.FileAssetService;
import com.studyagent.file.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileAssetServiceTest {
    @Mock FileStorageService storage;
    @Mock FileAssetMapper mapper;
    @InjectMocks FileAssetService service;

    @Test
    void storesValidatedImageAndReturnsApiUrl() throws Exception {
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile upload = new MockMultipartFile("file", "avatar.png", "image/png", pngHeader);
        when(storage.store(upload)).thenReturn(new FileStorageService.StoredFile("random.png", "avatar.png", "image/png", pngHeader.length));

        var result = service.upload(7L, "AVATAR", upload);

        assertEquals("/api/files/random.png", result.url());
        assertEquals("avatar.png", result.fileName());
        verify(mapper).insert(argThat(asset -> asset.getUserId().equals(7L)
                && asset.getType().equals("AVATAR") && asset.getStorageKey().equals("random.png")));
    }

    @Test
    void rejectsMimeOrSignatureMismatchBeforeStorage() {
        MockMultipartFile fake = new MockMultipartFile("file", "avatar.png", "image/png", "not a png".getBytes());
        assertThrows(BusinessException.class, () -> service.upload(7L, "AVATAR", fake));
        verifyNoInteractions(storage, mapper);
    }

    @Test
    void rejectsUntrustedType() {
        MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        assertThrows(BusinessException.class, () -> service.upload(7L, "SHELL", png));
        verifyNoInteractions(storage, mapper);
    }
}
