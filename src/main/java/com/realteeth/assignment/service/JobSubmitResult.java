package com.realteeth.assignment.service;

import com.realteeth.assignment.domain.entity.Job;

public record JobSubmitResult(Job job, boolean created) {}
