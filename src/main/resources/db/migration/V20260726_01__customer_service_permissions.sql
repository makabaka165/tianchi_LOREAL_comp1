-- BASE-003: customer service scope permissions.
-- Six external permission codes for the beauty-service-copilot contexts, bound to the
-- admin role. Fine-grained mapping for dedicated agent/risk-operator roles is deferred
-- to RELEASE-001; test fixtures must still cover least-privilege combinations.

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `status`, `remark`) VALUES
('cs:data:import', 'Customer service data import', 1, 'Preview and confirm XLSX service data imports'),
('cs:workspace:read', 'Customer service workspace read', 1, 'Read conversations, journeys and workbench data'),
('cs:assist:request', 'Customer service assistance request', 1, 'Create assistance requests for conversations'),
('cs:suggestion:decide', 'Customer service suggestion decide', 1, 'Accept, edit-accept or reject assistance suggestions'),
('cs:risk:read', 'Customer service risk read', 1, 'Read risk alerts, signals and disposition history'),
('cs:risk:manage', 'Customer service risk manage', 1, 'Acknowledge, assign, start, resolve or dismiss risk alerts')
ON DUPLICATE KEY UPDATE
  `permission_name` = VALUES(`permission_name`),
  `status` = VALUES(`status`),
  `remark` = VALUES(`remark`);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `status`)
SELECT r.id, p.id, 1
FROM `sys_role` r
JOIN `sys_permission` p ON p.permission_code IN (
  'cs:data:import',
  'cs:workspace:read',
  'cs:assist:request',
  'cs:suggestion:decide',
  'cs:risk:read',
  'cs:risk:manage')
WHERE r.role_key = 'admin';
