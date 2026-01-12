package com.example.bookreview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.example.bookreview.service.google.GoogleDriveService;
import com.example.bookreview.service.google.GoogleDriveServiceImpl;
import com.example.bookreview.util.GoogleDriveClientProvider;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleDriveServiceImplTest {

    @Mock
    private GoogleDriveClientProvider driveClientProvider;

    @Mock
    private Drive drive;

    @Mock
    private Drive.Files files;

    @Mock
    private Drive.Files.Create create;

    @Mock
    private Drive.Files.Delete delete;

    @Mock
    private Drive.Files.Get get;

    @Mock
    private File googleFile;

    private GoogleDriveService googleDriveService;

    @BeforeEach
    void setUp() {
        googleDriveService = new GoogleDriveServiceImpl(driveClientProvider);
    }

    @Test
    void uploadMarkdown_sanitizesFileNameAndReturnsId() throws Exception {
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        given(driveClientProvider.getDriveService()).willReturn(drive);
        given(drive.files()).willReturn(files);
        given(files.create(any(File.class), any())).willReturn(create);
        given(create.setFields("id")).willReturn(create);
        given(create.execute()).willReturn(googleFile);
        given(googleFile.getId()).willReturn("abc123");

        String fileId = googleDriveService.uploadMarkdown("운명과 분노!", "내용");

        assertThat(fileId).isEqualTo("abc123");
        assertThat(fileCaptor.getValue().getName()).isEqualTo("운명과-분노.md");
        verify(files).create(fileCaptor.capture(), any());
    }

    @Test
    void downloadFile_returnsStream() throws Exception {
        given(driveClientProvider.getDriveService()).willReturn(drive);
        given(drive.files()).willReturn(files);
        given(files.get("file123")).willReturn(get);
        given(get.execute()).willReturn(googleFile);
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(0)).write("hello".getBytes());
            return null;
        }).when(get).executeMediaAndDownloadTo(any());

        InputStream inputStream = googleDriveService.downloadFile("file123");

        byte[] buffer = inputStream.readAllBytes();
        assertThat(new ByteArrayInputStream(buffer)).hasContentEqualTo(new ByteArrayInputStream("hello".getBytes()));
    }

    @Test
    void downloadFile_throwsWhenMissing() throws Exception {
        given(driveClientProvider.getDriveService()).willReturn(drive);
        given(drive.files()).willReturn(files);
        given(files.get("missing")).willReturn(get);
        given(get.execute()).willReturn(null);

        assertThatThrownBy(() -> googleDriveService.downloadFile("missing"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void deleteFile_silentlyLogsWhenFailed() throws Exception {
        given(driveClientProvider.getDriveService()).willReturn(drive);
        given(drive.files()).willReturn(files);
        given(files.delete("file123")).willReturn(delete);

        googleDriveService.deleteFile("file123");

        verify(files).delete("file123");
    }
}
