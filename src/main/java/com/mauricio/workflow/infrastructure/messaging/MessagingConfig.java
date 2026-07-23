package com.mauricio.workflow.infrastructure.messaging;

import com.mauricio.workflow.infrastructure.persistence.OutboxJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.time.Clock;
import java.util.Map;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.sqs", name = "enabled", havingValue = "true")
public class MessagingConfig {

    @Bean
    SqsClient sqsClient(@Value("${app.sqs.endpoint:}") String endpoint,
                        @Value("${app.aws.region:us-east-1}") String region) {
        var builder = SqsClient.builder().region(Region.of(region));
        // A local endpoint (LocalStack) needs an explicit override and dummy
        // credentials; against real AWS both come from the default chains.
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        }
        return builder.build();
    }

    @Bean
    QueueRegistry queueRegistry(SqsClient sqs,
                                @Value("${app.sqs.queue-name}") String queueName,
                                @Value("${app.sqs.dlq-name}") String dlqName) {
        String dlqUrl = sqs.createQueue(b -> b.queueName(dlqName)).queueUrl();
        String dlqArn = sqs.getQueueAttributes(b -> b.queueUrl(dlqUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);

        String redrivePolicy =
                "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}";

        String mainUrl = sqs.createQueue(b -> b.queueName(queueName)
                .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, redrivePolicy))).queueUrl();

        return new QueueRegistry(mainUrl, dlqUrl);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxJpaRepository outbox, SqsClient sqs,
                            QueueRegistry queues, Clock clock) {
        return new OutboxRelay(outbox, sqs, queues, clock);
    }
}
