ALTER TABLE travel_plan_items
    ADD CONSTRAINT ck_travel_plan_items_no_stay CHECK (item_type <> 'STAY');
