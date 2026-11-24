package org.imprime.ai.api.quote;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QuotePartCalculatorRegistry {
    private final Map<String, QuotePartCalculator> calculatorsByKey;
    private final QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;

    public QuotePartCalculatorRegistry(
            List<QuotePartCalculator> calculators,
            QuoteSettingsInMemoryCache quoteSettingsInMemoryCache) {
        this.quoteSettingsInMemoryCache = quoteSettingsInMemoryCache;

        Map<String, QuotePartCalculator> map = new LinkedHashMap<>();
        if (calculators != null) {
            for (QuotePartCalculator calculator : calculators) {
                if (calculator == null) {
                    continue;
                }
                registerKey(map, calculator.calculatorKey(), calculator);
                registerKey(map, ClassUtils.getUserClass(calculator).getName(), calculator);
            }
        }
        this.calculatorsByKey = Map.copyOf(map);
    }

    public QuotePartCalculator resolve(String calculatorClass) {
        if (calculatorClass == null || calculatorClass.isBlank()) {
            log.error("Missing quote_calculator_class on active quote module configuration");
            throw new InternalErrorException("Calculadora de orçamento não informada.");
        }
        QuotePartCalculator calculator = calculatorsByKey.get(calculatorClass.trim());
        if (calculator == null) {
            log.error("Unregistered quote_calculator_class [{}]. Registered keys: {}",
                    calculatorClass, calculatorsByKey.keySet());
            throw new InternalErrorException("Calculadora de orçamento não registrada: " + calculatorClass);
        }
        return calculator;
    }

    public void validateActiveCatalog(List<QuoteSettings> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        for (QuoteSettings module : catalog) {
            if (module == null) {
                continue;
            }
            String className = module.getQuoteCalculatorClass();
            if (className == null || className.isBlank()) {
                throw new IllegalStateException(
                        "QuoteSettings [" + module.getCode() + "] does not declare quote_calculator_class");
            }
            QuotePartCalculator calculator = calculatorsByKey.get(className.trim());
            if (calculator == null) {
                throw new IllegalStateException(
                        "QuoteSettings [" + module.getCode() + "] references unknown calculator bean [" + className + "]");
            }
            if (calculator.supportedType() != module.getQuoteType()) {
                throw new IllegalStateException(
                        "QuoteSettings [" + module.getCode() + "] has type [" + module.getQuoteType()
                                + "] incompatible with calculator [" + className + "] type [" + calculator.supportedType() + "]");
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateCatalogOnStartup() {
        List<QuoteSettings> activeCatalog = quoteSettingsInMemoryCache != null
                ? quoteSettingsInMemoryCache.getAllOrdered()
                : List.of();
        if (activeCatalog.isEmpty()) {
            log.warn("QuoteSettings catalog cache is empty on ApplicationReadyEvent; skipping fail-fast validation");
            return;
        }
        validateActiveCatalog(activeCatalog);
        log.info("Validated {} active quote modules against {} registered calculator keys",
                activeCatalog.size(), calculatorsByKey.size());
    }

    private void registerKey(
            Map<String, QuotePartCalculator> map,
            String rawKey,
            QuotePartCalculator calculator) {
        if (rawKey == null || rawKey.isBlank()) {
            return;
        }
        String key = rawKey.trim();
        QuotePartCalculator previous = map.putIfAbsent(key, calculator);
        if (previous != null && previous != calculator) {
            throw new IllegalStateException("Duplicate QuotePartCalculator key registered: [" + key + "]");
        }
    }
}
