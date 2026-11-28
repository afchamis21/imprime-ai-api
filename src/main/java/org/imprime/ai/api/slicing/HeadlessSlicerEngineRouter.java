package org.imprime.ai.api.slicing;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeadlessSlicerEngineRouter implements HeadlessSlicerEngine {

    private final SlicingValidationProperties properties;
    private final BambuStudioCliSlicerEngine bambuEngine;
    private final OrcaSlicerCliSlicerEngine orcaEngine;
    private final DockerCliSlicerEngine dockerEngine;

    @Override
    public HeadlessSliceResult slice(HeadlessSliceRequest request) {
        return switch (properties.getProvider()) {
            case ORCA_SLICER -> orcaEngine.slice(request);
            case DOCKER -> dockerEngine.slice(request);
            case BAMBU_STUDIO -> bambuEngine.slice(request);
        };
    }
}
