package org.imprime.ai.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "imprime.ai.slicing")
public class SlicingValidationProperties {

    public enum ExecutionMode {
        SYNC,
        ASYNC,
        HYBRID
    }

    public enum SlicerProvider {
        BAMBU_STUDIO,
        ORCA_SLICER,
        DOCKER
    }

    public enum DivergencePolicy {
        RECALCULATE,
        REJECT
    }

    private boolean enabled = true;
    private ExecutionMode executionMode = ExecutionMode.HYBRID;
    private SlicerProvider provider = SlicerProvider.BAMBU_STUDIO;
    private String binaryPath = "bambu-studio";
    private String dockerImage = "imprimeai/slicer-cli:latest";
    private String profilesDir = "";
    private int maxConcurrentJobs = 2;
    private int perPlateTimeoutSeconds = 30;
    private int syncMaxPlates = 2;
    private long syncMaxBytes = 15_728_640L;
    private int maxTrianglesPerPlate = 1_500_000;
    private boolean storeValidatedArtifacts = true;
    private Validation validation = new Validation();
    private Rabbitmq rabbitmq = new Rabbitmq();

    @Data
    public static class Validation {
        private double weightTolerancePercent = 5.0;
        private double minWeightToleranceGrams = 2.0;
        private double timeTolerancePercent = 10.0;
        private int minTimeToleranceSeconds = 120;
        private double hardRejectTolerancePercent = 30.0;
        private DivergencePolicy divergencePolicy = DivergencePolicy.RECALCULATE;
        private boolean failOpenOnUnavailable = false;
    }

    @Data
    public static class Rabbitmq {
        private String exchange = "imprimeai.slicing.exchange";
        private String queue = "imprimeai.slicing.queue";
        private String routingKey = "slicing.validate";
        private String dlx = "imprimeai.slicing.dlx";
        private String dlq = "imprimeai.slicing.dlq";
        private String dlqRoutingKey = "slicing.dlq";
    }
}
