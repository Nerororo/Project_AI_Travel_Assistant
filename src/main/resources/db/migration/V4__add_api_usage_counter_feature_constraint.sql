ALTER TABLE api_usage_counters
    ADD CONSTRAINT ck_api_usage_counters_feature
        CHECK (feature IN (
            'PLACE_SEARCH',
            'CAR_ROUTE',
            'PUBLIC_TRANSIT_ROUTE',
            'AI_REGION_RECOMMENDATION',
            'AI_MENU_ANALYSIS'
        ));
