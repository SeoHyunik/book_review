package com.example.bookreview.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleDriveServiceImpl implements GoogleDriveService {

    @Override
    public String uploadMarkdown(String filename, String markdownContent) {
        log.info("Uploading markdown to Google Drive mock: filename='{}', contentLength={}", filename, markdownContent.length());
        // TODO: Google Drive API 연동하여 파일 업로드
        String fileId = "fake-file-id";
        log.debug("Returning placeholder Google Drive fileId={}", fileId);
        return fileId;
    }

    @Override
    public InputStream downloadFile(String fileId) {
        log.info("Downloading file from Google Drive mock: fileId={}", fileId);
        // TODO: Google Drive 파일 다운로드 구현
        return new ByteArrayInputStream(("파일 " + fileId + " 의 더미 컨텐츠").getBytes(StandardCharsets.UTF_8));
    }
}
