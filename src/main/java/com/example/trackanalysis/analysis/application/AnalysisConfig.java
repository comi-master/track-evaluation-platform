package com.example.trackanalysis.analysis.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AnalysisProperties.class, AnalysisCacheProperties.class})
public class AnalysisConfig {}
