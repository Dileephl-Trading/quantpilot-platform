ALTER TABLE execution_request ADD COLUMN excess_exit_quantity BIGINT NOT NULL DEFAULT 0;

-- Backfill linked overfills on retained historical entries, including records outside today's order book.
UPDATE execution_request parent
SET excess_exit_quantity = exits.total - parent.filled_quantity
FROM (
    SELECT parent_order_id, SUM(filled_quantity) AS total
    FROM execution_request
    WHERE protective AND parent_order_id IS NOT NULL
    GROUP BY parent_order_id
) exits
WHERE parent.order_id = exits.parent_order_id AND NOT parent.protective
  AND exits.total > parent.filled_quantity;
