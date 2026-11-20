-- Indexes for the default order of the paged lists.
CREATE INDEX student_name_order ON student (full_name, id);
CREATE INDEX absence_record_date_order ON absence_record (absence_date, id);
CREATE INDEX stay_checkout_order ON stay (check_out_at, id);
