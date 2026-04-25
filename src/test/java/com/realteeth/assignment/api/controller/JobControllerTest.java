package com.realteeth.assignment.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realteeth.assignment.api.dto.JobSubmitRequest;
import com.realteeth.assignment.domain.entity.Job;
import com.realteeth.assignment.domain.enums.JobStatus;
import com.realteeth.assignment.exception.ErrorCode;
import com.realteeth.assignment.exception.JobException;
import com.realteeth.assignment.service.JobService;
import com.realteeth.assignment.service.JobSubmitResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JobService jobService;

    private static final String IMAGE_URL = "https://example.com/image.jpg";

    @Test
    void POST_잡_제출_신규_생성_시_201을_반환한다() throws Exception {
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobService.submitJob(IMAGE_URL)).willReturn(new JobSubmitResult(job, true));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobSubmitRequest(IMAGE_URL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.imageUrl").value(IMAGE_URL));
    }

    @Test
    void POST_잡_제출_중복_요청_시_200을_반환한다() throws Exception {
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobService.submitJob(IMAGE_URL)).willReturn(new JobSubmitResult(job, false));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobSubmitRequest(IMAGE_URL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void POST_잡_제출_imageUrl이_빈_값이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobSubmitRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_REQUEST"));
    }

    @Test
    void POST_잡_제출_imageUrl이_null이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobSubmitRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_REQUEST"));
    }

    @Test
    void POST_잡_제출_요청_body가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_REQUEST"));
    }

    @Test
    void GET_단건_조회_존재하는_잡이면_200을_반환한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        Job job = Job.create(IMAGE_URL, "hash");
        given(jobService.getJob(jobId)).willReturn(job);

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.imageUrl").value(IMAGE_URL));
    }

    @Test
    void GET_단건_조회_존재하지_않는_잡이면_404를_반환한다() throws Exception {
        UUID jobId = UUID.randomUUID();
        given(jobService.getJob(jobId)).willThrow(new JobException(ErrorCode.JOB_NOT_FOUND));

        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void GET_목록_조회_기본_파라미터로_200을_반환한다() throws Exception {
        Page<Job> page = new PageImpl<>(List.of(Job.create(IMAGE_URL, "hash")));
        given(jobService.getJobs(eq(null), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void GET_목록_조회_status_필터로_조회한다() throws Exception {
        Job job = Job.create(IMAGE_URL, "hash");
        Page<Job> page = new PageImpl<>(List.of(job));
        given(jobService.getJobs(eq(JobStatus.PENDING), any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/api/v1/jobs").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void GET_목록_조회_잘못된_status_값이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/jobs").param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }
}
