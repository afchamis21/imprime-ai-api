DELETE
FROM message_lkup
WHERE CODE = 'PHO_001'
  AND LANGUAGE_CODE IN ('en-US', 'pt-BR', 'es-ES');