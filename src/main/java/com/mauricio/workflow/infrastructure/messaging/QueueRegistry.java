package com.mauricio.workflow.infrastructure.messaging;

public class QueueRegistry {

    private final String mainQueueUrl;
    private final String deadLetterQueueUrl;

    public QueueRegistry(String mainQueueUrl, String deadLetterQueueUrl) {
        this.mainQueueUrl = mainQueueUrl;
        this.deadLetterQueueUrl = deadLetterQueueUrl;
    }

    public String mainQueueUrl() {
        return mainQueueUrl;
    }

    public String deadLetterQueueUrl() {
        return deadLetterQueueUrl;
    }
}
