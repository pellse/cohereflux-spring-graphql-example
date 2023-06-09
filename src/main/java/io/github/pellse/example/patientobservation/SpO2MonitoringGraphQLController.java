package io.github.pellse.example.patientobservation;

import io.github.pellse.cohereflux.CohereFlux;
import io.github.pellse.example.patientobservation.bodymeasurement.BodyMeasurement;
import io.github.pellse.example.patientobservation.bodymeasurement.BodyMeasurementService;
import io.github.pellse.example.patientobservation.patient.Patient;
import io.github.pellse.example.patientobservation.patient.PatientService;
import io.github.pellse.example.patientobservation.spo2.SpO2;
import io.github.pellse.example.patientobservation.spo2.SpO2StreamingService;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import static io.github.pellse.cohereflux.CohereFluxBuilder.cohereFluxOf;
import static io.github.pellse.cohereflux.Rule.rule;
import static io.github.pellse.cohereflux.RuleMapper.oneToOne;
import static io.github.pellse.cohereflux.RuleMapperSource.call;
import static io.github.pellse.cohereflux.caching.CacheFactory.cached;
import static java.time.Duration.ofSeconds;

@Controller
public class SpO2MonitoringGraphQLController {

    private final CohereFlux<SpO2, SpO2Reading> spO2ReadingCohereFlux;

    private final SpO2StreamingService spO2StreamingService;

    SpO2MonitoringGraphQLController(
            PatientService patientService,
            BodyMeasurementService bodyMeasurementService,
            SpO2StreamingService spO2StreamingService) {

        this.spO2StreamingService = spO2StreamingService;

        spO2ReadingCohereFlux = cohereFluxOf(SpO2Reading.class)
                .withCorrelationIdResolver(SpO2::patientId)
                .withRules(
                        rule(Patient::id, oneToOne(cached(call(SpO2::healthCardNumber, patientService::findPatientsByHealthCardNumber)))),
                        rule(BodyMeasurement::patientId, oneToOne(cached(call(bodyMeasurementService::getBodyMeasurements)))),
                        SpO2Reading::new)
                .build();
    }

    @SubscriptionMapping
    Flux<SpO2Reading> spO2Reading() {

        return spO2StreamingService.spO2Flux()
                .window(3)
                .flatMapSequential(spO2ReadingCohereFlux::process)
                .delayElements(ofSeconds(1));
    }

    record SpO2Reading(SpO2 spO2, Patient patient, BodyMeasurement bodyMeasurement) {
    }
}
