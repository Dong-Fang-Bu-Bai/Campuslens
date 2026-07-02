ALTER TABLE correction_sample
  ADD COLUMN evaluation_status VARCHAR(50) NOT NULL DEFAULT 'legacy_unavailable';

UPDATE correction_sample
SET evaluation_status = CASE
  WHEN review_score IS NOT NULL THEN 'completed'
  ELSE 'legacy_unavailable'
END;
