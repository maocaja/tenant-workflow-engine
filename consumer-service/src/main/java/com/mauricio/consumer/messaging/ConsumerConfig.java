package com.mauricio.consumer.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.consumer.persistence.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Clock;

@Configuration
public class ConsumerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    SqsClient sqsClient(@Value("${app.sqs.endpoint:}") String endpoint,
                        @Value("${app.aws.region:us-east-1}") String region) {
        var builder = SqsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        }
        return builder.build();
    }

    @Bean
    String queueUrl(SqsClient sqs, @Value("${app.sqs.queue-name}") String queueName) {
        return sqs.createQueue(b -> b.queueName(queueName)).queueUrl();
    }

    @Bean
    ApprovalConsumer approvalConsumer(SqsClient sqs, String queueUrl,
                                      ProcessedEventRepository processed,
                                      ObjectMapper objectMapper, Clock clock) {
        return new ApprovalConsumer(sqs, queueUrl, processed, objectMapper, clock);
    }
}
