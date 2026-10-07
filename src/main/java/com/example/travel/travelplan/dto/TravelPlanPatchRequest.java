package com.example.travel.travelplan.dto;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TravelPlanPatchRequest(String title, List<PlaceEdit> placeEdits) {
    private static final Set<String> FIELDS = Set.of("title", "placeEdits");
    private static final Set<String> PLACE_FIELDS = Set.of("planPlaceId", "displayName", "memo");

    public record PlaceEdit(long planPlaceId, String displayName, boolean changeName,
                            String memo, boolean changeMemo) { }

    public static TravelPlanPatchRequest from(Map<String, Object> body) {
        if (body == null || body.isEmpty() || !FIELDS.containsAll(body.keySet())) throw invalid();
        String title = null;
        if (body.containsKey("title")) {
            title = requiredText(body.get("title"), 100);
        }
        List<PlaceEdit> edits = new ArrayList<>();
        if (body.containsKey("placeEdits")) {
            if (!(body.get("placeEdits") instanceof List<?> values)) throw invalid();
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> map) || !PLACE_FIELDS.containsAll(map.keySet())
                        || !map.containsKey("planPlaceId")
                        || (!map.containsKey("displayName") && !map.containsKey("memo"))) throw invalid();
                Object id = map.get("planPlaceId");
                if (!(id instanceof Integer || id instanceof Long) || ((Number) id).longValue() <= 0) throw invalid();
                boolean changeName = map.containsKey("displayName");
                String name = changeName ? requiredText(map.get("displayName"), 50) : null;
                boolean changeMemo = map.containsKey("memo");
                Object memoValue = map.get("memo");
                if (changeMemo && memoValue != null
                        && (!(memoValue instanceof String text) || text.length() > 1000)) throw invalid();
                edits.add(new PlaceEdit(((Number) id).longValue(), name, changeName,
                        (String) memoValue, changeMemo));
            }
        }
        if (title == null && edits.isEmpty()) throw invalid();
        return new TravelPlanPatchRequest(title, List.copyOf(edits));
    }

    private static String requiredText(Object value, int maxLength) {
        if (!(value instanceof String text) || text.trim().isEmpty() || text.trim().length() > maxLength)
            throw invalid();
        return text.trim();
    }

    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED); }
}
