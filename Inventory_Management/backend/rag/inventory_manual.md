# Inventory Management & Procurement Operations Manual
## POC-07 - Retail Inventory Reference Guide

### Section 1: Introduction to Inventory Management
Inventory management is the process of ordering, storing, and using a company's inventory, including raw materials, components, and finished products.
Effective inventory management ensures products are available when customers need them while minimizing carrying costs and reducing overstock risk.
This system manages four main activities: product catalog management, stock level tracking, purchase order management, and stock movement recording.
All inventory changes must be recorded as movement events so that audits can trace who changed what, when, and why.
This manual is the authoritative source for Phase 2 retrieval and answer generation.
The assistant must answer using this manual content only.

### Section 2: Product Catalog and SKU System
Every product has a unique SKU (Stock Keeping Unit) in the format SKU-{CATEGORY_PREFIX}-{NNNN}.
Category prefixes are standardized:
- GRO: Grocery products
- ELC: Electronics
- CLO: Clothing and apparel
- HHD: Household items
- PRC: Personal care products
Examples:
- SKU-GRO-0042 means Grocery item number 42.
- SKU-ELC-0015 means Electronics item number 15.
- SKU-CLO-0101 means Clothing item number 101.
Each product stores unit_price, cost_price, unit_of_measure, reorder_point, reorder_quantity, and supplier mapping.
Unit of measure values include pieces, kg, litre, and box.
The catalog should avoid duplicate SKUs and duplicate active product names per supplier.

### Section 3: Stock Levels and Reorder Points
Every product has a StockLevel record with:
- quantity_on_hand: physical stock count
- quantity_reserved: stock committed to pending orders
- quantity_available: quantity_on_hand minus quantity_reserved
The quantity_available field is the primary field for replenishment decisions.
A reorder point indicates when replenishment should start.
A commonly used rule is:
reorder_point = (average daily demand x supplier lead time days) + safety stock.
Example:
Daily demand is 10 units, lead time is 5 days, and safety stock is 2 days of demand.
Reorder point = (10 x 5) + (10 x 2) = 70 units.
Teams should tune reorder points monthly based on trends.

### Section 4: Stock Alert System
The system automatically generates alerts based on stock conditions.
Low Stock Alert:
- Trigger condition: quantity_available <= reorder_point
- Severity: warning
- Action: raise PO within 24-48 hours
Out of Stock Alert:
- Trigger condition: quantity_available = 0
- Severity: critical
- Action: immediate procurement action
Reorder Suggested Alert:
- Trigger condition: usage trend indicates likely stockout before next review cycle
- Severity: advisory
Resolved alerts remain in the audit log and must never be hard deleted.
The system should avoid duplicate unresolved alerts for the same product and alert type.

### Section 5: Purchase Order (PO) Process
Purchase orders are formal requests to suppliers.
PO lifecycle states:
1. Draft: Procurement officer creates PO and adds line items.
2. Submitted: PO sent to supplier.
3. Acknowledged: Supplier confirms receipt and expected delivery.
4. Received: Goods are received and inventory is updated.
5. Cancelled: PO stopped before receipt.
PO number format is PO-{YEAR}-{NNNN}, for example PO-2026-0042.
When a PO is marked as received, the system must:
- create StockMovement records of movement_type=receipt for each item
- increase quantity_on_hand per received quantity
- recompute quantity_available
- resolve low_stock or out_of_stock alerts for received products
Receipt should be idempotent at API level to avoid double posting stock.

### Section 6: Supplier Management
Suppliers include these fields:
- supplier_code in SUP-0001 format
- lead_time_days
- payment_terms_days
- is_active
Only active suppliers should receive new purchase orders.
Supplier evaluation criteria:
- price competitiveness
- lead time
- delivery reliability
- payment flexibility
Before creating a PO, verify supplier is_active is true and supplier catalog has matching SKU.
Inactive suppliers can remain for historical reporting but cannot be used for new order creation.

### Section 7: Stock Movement Recording
Every inventory change must be represented as StockMovement.
Allowed movement_type values:
- receipt: goods received from purchase order (positive quantity)
- sale: goods sold to customer (negative quantity)
- adjustment: manual correction after physical count
- transfer: movement between warehouses
- return: customer return or supplier return
reference_number should map to source documents such as PO number or sale order number.
All movements are timestamped and attributed to a staff identity.
A high quality audit log captures movement_type, quantity, reference_number, recorded_by, and recorded_at.

### Section 8: Inventory Valuation
Inventory valuation is based on cost_price and FIFO (First In, First Out) flow.
Total stock value formula:
Total stock value = SUM(product.cost_price x stock_level.quantity_on_hand)
across all active products.
Management reviews total_stock_value on the dashboard to understand working capital usage.
Overstock increases carrying cost and potential markdown risk.
Stock turns should be optimized by balancing service level and inventory investment.

### Section 9: Reorder Quantity Calculation
Economic Order Quantity (EOQ) formula:
EOQ = sqrt((2 x annual_demand x ordering_cost) / holding_cost_per_unit)
Many retailers use a practical planning shortcut:
reorder_quantity = demand_per_day x (lead_time_days + safety_stock_days)
Example:
Demand = 5/day, lead time = 7 days, safety = 14 days.
Reorder quantity = 5 x (7 + 14) = 105 units.
The chosen method should be documented by category and reviewed quarterly.

### Section 10: Procurement Officer Responsibilities
Procurement Officer: Anita Singh.
Responsibilities:
1. Review low_stock and out_of_stock alerts daily.
2. Compare supplier catalogs and costs.
3. Raise purchase orders within 24 hours of low stock warning.
4. Ensure supplier acknowledges submitted PO.
5. Coordinate receiving with warehouse staff.
Approval rule:
POs above Rs. 50,000 require Store Manager approval before submission.
POs at or below Rs. 50,000 can be submitted by procurement without manager approval.

### Section 11: Stock Count and Reconciliation
Cycle count and full count policies:
- Cycle count: subset counted daily or weekly; high-value items at least weekly.
- Full count: full inventory count quarterly.
When variance is found:
- Record StockMovement(adjustment) for the delta.
- Positive adjustment means physical stock is greater than system stock.
- Negative adjustment means shrinkage, damage, theft, or prior entry error.
Reconciliation logs should include root-cause notes for audit and corrective action.

### Section 12: Category Management
Category behavior differences:
- Grocery: short shelf life, high velocity, strict reorder discipline.
- Electronics: high unit cost, lower velocity, usually longer lead time.
- Clothing: seasonal demand, variant complexity (size/color).
- Household: moderate velocity, stable repeat demand.
- Personal Care: high brand sensitivity and competitive pricing.
Category-specific reorder_point and safety_stock tuning improves service level and reduces write-off.

### Section 13: Reporting and Analytics
Key reports:
- Slow-moving stock: no movement for 30+ days.
- Stock turn ratio: cost of goods sold / average inventory value.
- Fill rate: percentage of demand fulfilled without stockout.
- Days on hand: quantity_on_hand / average daily sales.
Operating cadence:
- Store Manager (Priya Sharma) reviews weekly dashboards.
- Inventory Analyst (Raj Patel) publishes monthly trend reports.
Recommended analytics dimensions:
- category
- supplier
- warehouse
- aging bucket
- stockout frequency

### Section 14: System Integration
Integration touchpoints:
- Point of Sale (POS): auto-create sale movements.
- Supplier portal: submit and acknowledge POs.
- Finance: push PO values to accounts payable.
If integration is unavailable, manual StockMovement entries are required.
Manual entries should be validated by role-based approval when they exceed predefined thresholds.
Retry and reconciliation jobs should detect and repair missed integration events.

### Section 15: Troubleshooting
Problem: Stock level is negative.
Cause: sale posted before receipt or incorrect manual entry.
Fix: post positive adjustment and investigate sequence.

Problem: Duplicate low stock alerts.
Cause: repeated trigger without checking unresolved alerts.
Fix: enforce unresolved-alert existence check before creating new alert.

Problem: PO received but stock not updated.
Cause: PATCH /orders/{id}/receive was not called or failed silently.
Fix: inspect API logs, verify PO status=received, and manually trigger receipt workflow if needed.

Problem: Supplier cannot find SKU.
Cause: product exists internally but not mapped in supplier catalog.
Fix: update supplier catalog, verify supplier_id linkage on product.

Problem: Frequent stockouts despite reorder process.
Cause: reorder point not accounting for lead time variability.
Fix: increase safety stock and review supplier lead_time_days distribution.

Problem: Overstock in low velocity products.
Cause: reorder quantity too high for demand profile.
Fix: lower reorder quantity and increase review cadence for slow movers.

### Appendix A: Operational Control Checklist
Daily controls:
- Review all unresolved low_stock and out_of_stock alerts by 9:00 AM.
- Verify all previous-day receipts generated movement_type=receipt entries with PO reference.
- Verify no product has quantity_available below zero.
- Confirm all manual adjustments include notes and recorded_by.
- Validate that every submitted PO has supplier acknowledgement within agreed SLA.

Weekly controls:
- Review top 20 products by stockout incidents and adjust reorder policies.
- Review top 20 products by overstock days and reduce excess procurement.
- Validate supplier lead_time_days against actual delivery lead time trends.
- Review duplicate alert incidents and close root causes.
- Review received POs with short delivery or excess delivery and document variances.

Monthly controls:
- Recompute reorder_point and reorder_quantity by category.
- Compare forecasted demand versus actual demand.
- Run cycle count variance analysis and assign CAPA actions.
- Publish category-wise fill rate and stock turn ratio to management.

### Appendix B: Worked Examples
Example 1: Low stock trigger and PO action
Product SKU-GRO-0042 has quantity_on_hand=82 and quantity_reserved=17.
quantity_available = 82 - 17 = 65.
If reorder_point is 70, a low_stock alert must be generated.
Procurement officer should create draft PO within 24 hours.

Example 2: Out-of-stock critical alert
Product SKU-ELC-0015 has quantity_on_hand=5 and quantity_reserved=5.
quantity_available = 0, so out_of_stock alert must trigger immediately.
Store team should expedite emergency procurement and substitute recommendations.

Example 3: Receipt updates stock and closes alert
PO-2026-0042 includes 100 units of SKU-PRC-0090.
Before receipt, quantity_on_hand=12 and quantity_reserved=2.
On receipt, quantity_on_hand becomes 112 and quantity_available becomes 110.
Any unresolved low_stock or out_of_stock alert for SKU-PRC-0090 should be resolved.

Example 4: Inventory valuation snapshot
SKU-GRO-0042 cost_price=28 and quantity_on_hand=300 contributes 8,400.
SKU-ELC-0015 cost_price=1,800 and quantity_on_hand=25 contributes 45,000.
SKU-CLO-0101 cost_price=650 and quantity_on_hand=70 contributes 45,500.
Total stock value contribution from these three products is 98,900.

### Appendix C: Data Quality Rules
- SKU must follow SKU-{CATEGORY_PREFIX}-{NNNN} pattern.
- PO number must follow PO-{YEAR}-{NNNN} pattern.
- supplier_code must follow SUP-{NNNN} pattern.
- quantity_reserved cannot exceed quantity_on_hand for active stock levels.
- quantity_available must always be quantity_on_hand minus quantity_reserved.
- movement_type must be one of receipt, sale, adjustment, transfer, return.
- received PO must have received_date populated.
- cancelled PO must never post receipt movements.
- inactive suppliers cannot be used for new PO creation.
- unresolved duplicate alerts for same product and alert type are not allowed.
