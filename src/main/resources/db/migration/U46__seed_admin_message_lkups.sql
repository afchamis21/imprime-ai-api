DELETE FROM message_lkup
WHERE code IN (
    'ADM_001', 'ADM_002', 'ADM_003', 'ADM_004',
    'USR_024',
    'REF_001', 'REF_002', 'REF_003',
    'FIN_002', 'FIN_003',
    'AUD_001'
);
