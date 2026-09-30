-- V2: начальное состояние источников. Все пять источников включены,
-- draining выключен (ADR-VL-04, ADR-VL-17). Идемпотентно: повторное
-- применение не перетирает состояние, выставленное админом.

INSERT INTO storm.source_state (source, enabled, draining, updated_at) VALUES
    ('atlasbus',  TRUE, FALSE, NOW()),
    ('ticketbus', TRUE, FALSE, NOW()),
    ('bzd',       TRUE, FALSE, NOW()),
    ('ticketpro', TRUE, FALSE, NOW()),
    ('belhotel',  TRUE, FALSE, NOW())
ON CONFLICT (source) DO NOTHING;
