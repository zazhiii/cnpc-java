package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class E11GateReportParserTest {

    private final E11GateReportParser parser = new E11GateReportParser(new ObjectMapper());

    @Test
    void shouldParsePassedReport() {
        String json = """
                {
                  "model_version":"E11",
                  "passed":true,
                  "reason":"passed",
                  "threshold":0.75,
                  "min_detections":1,
                  "accepted_count":1,
                  "candidate_count":2,
                  "rejection_counts":{"below_threshold":1,"invalid_polygon":0,"unexpected_label":0},
                  "confidence_summary":{"minimum":0.91,"maximum":0.91,"mean":0.91},
                  "image":{"path":"secret/input.jpg","width":1920,"height":1080},
                  "detections":[{"label":"CUTTING_TOOTH","confidence":0.91,
                    "polygon":[[1.0,2.0],[3.0,4.0],[5.0,6.0]]}],
                  "inference_speed_ms":{"preprocess":2.1,"inference":30.2}
                }
                """;

        ImageQualityModelResult result = parser.parse(json, 0, 0.75, 1);

        assertTrue(result.getPassed());
        assertEquals(1, result.getAcceptedCount());
        assertEquals(1920, result.getImage().getWidth());
        assertEquals(3, result.getDetections().get(0).getPolygon().size());
        assertEquals(30.2, result.getInferenceSpeedMs().get("inference"));
    }

    @Test
    void shouldTreatExitCodeTenAsNormalRejectedReport() {
        String json = """
                {
                  "model_version":"E11",
                  "passed":false,
                  "reason":"insufficient_detections_above_threshold",
                  "threshold":0.8,
                  "min_detections":1,
                  "accepted_count":0,
                  "candidate_count":0,
                  "rejection_counts":{"below_threshold":0,"invalid_polygon":0,"unexpected_label":0},
                  "confidence_summary":{"minimum":null,"maximum":null,"mean":null},
                  "image":{"width":640,"height":480},
                  "detections":[],
                  "inference_speed_ms":{}
                }
                """;

        ImageQualityModelResult result = parser.parse(json, 10, 0.8, 1);

        assertFalse(result.getPassed());
        assertEquals(0, result.getAcceptedCount());
    }

    @Test
    void shouldRejectInconsistentExitCode() {
        String json = """
                {"model_version":"E11","passed":false,"reason":"rejected","threshold":0.75,
                 "min_detections":1,"accepted_count":0,"candidate_count":0,"rejection_counts":{},
                 "confidence_summary":{"minimum":null,"maximum":null,"mean":null},
                 "image":{"width":10,"height":10},"detections":[],"inference_speed_ms":{}}
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json, 0, 0.75, 1));
    }

    @Test
    void shouldRejectMalformedPolygon() {
        String json = """
                {"model_version":"E11","passed":true,"reason":"passed","threshold":0.75,
                 "min_detections":1,"accepted_count":1,"candidate_count":1,"rejection_counts":{},
                 "confidence_summary":{"minimum":0.9,"maximum":0.9,"mean":0.9},
                 "image":{"width":10,"height":10},
                 "detections":[{"label":"CUTTING_TOOTH","confidence":0.9,"polygon":[[1,2],[3,4]]}],
                 "inference_speed_ms":{}}
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json, 0, 0.75, 1));
    }

}
