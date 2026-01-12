package com.example.bookreview.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookreview.dto.internal.IntegrationStatus;
import com.example.bookreview.dto.domain.Review;
import com.example.bookreview.repository.ReviewRepository;
import com.example.bookreview.service.google.GoogleDriveService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewDeletionIntegrationTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0.12");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockBean
    private GoogleDriveService googleDriveService;

    @BeforeEach
    void setup() {
        reviewRepository.deleteAll();
    }

    @Test
    void anonymousDeleteIsRedirectedToLogin() throws Exception {
        Review review = reviewRepository.save(sampleReview("r-anon"));

        mockMvc.perform(delete("/reviews/" + review.id()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void authenticatedDeleteRemovesReviewAndDriveFile() throws Exception {
        Review review = reviewRepository.save(sampleReview("r-1"));

        mockMvc.perform(delete("/reviews/" + review.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.driveDeleted").value(true))
                .andExpect(jsonPath("$.warnings").isArray());

        assertThat(reviewRepository.findById(review.id())).isEmpty();
        verify(googleDriveService, times(1)).deleteFile(review.googleFileId());
    }

    @Test
    void driveDeleteFailureStillDeletesRecordAndReturnsWarning() throws Exception {
        Review review = reviewRepository.save(sampleReview("r-2"));
        doThrow(new RuntimeException("drive down")).when(googleDriveService).deleteFile(anyString());

        mockMvc.perform(delete("/reviews/" + review.id())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.driveDeleted").value(false))
                .andExpect(jsonPath("$.warnings[0]").exists());

        assertThat(reviewRepository.findById(review.id())).isEmpty();
    }

    private Review sampleReview(String id) {
        return new Review(
                id,
                "샘플 제목",
                "원본",
                "개선",
                10L,
                new BigDecimal("1.23"),
                new BigDecimal("1000"),
                "drive-file-id",
                "owner-1",
                new IntegrationStatus(IntegrationStatus.Status.SUCCESS, IntegrationStatus.Status.SUCCESS,
                        IntegrationStatus.Status.SUCCESS, null),
                LocalDateTime.now()
        );
    }
}
