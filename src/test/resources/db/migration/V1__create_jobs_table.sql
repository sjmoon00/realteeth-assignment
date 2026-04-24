CREATE TABLE jobs
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id        VARCHAR(36)   NOT NULL UNIQUE,
    status        VARCHAR(20)   NOT NULL,
    request_hash  VARCHAR(64)   NOT NULL,
    image_url     VARCHAR(2048),
    worker_job_id VARCHAR(255),
    result        TEXT,
    error_message TEXT,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL
);

CREATE INDEX idx_jobs_status_created_at ON jobs (status, created_at);
CREATE INDEX idx_jobs_request_hash ON jobs (request_hash);
CREATE INDEX idx_jobs_worker_job_id ON jobs (worker_job_id);
