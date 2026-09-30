package com.workspace.storm.event.db.metrics;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.Signature;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Замер времени и числа строк у каждого вызова репозитория (ADR-039).
 *
 * <p>Точка среза - публичные методы пакета {@code db.repository}: это ровно
 * те вызовы, которые мы контролируем, и они не включают работу Flyway,
 * Flyway-историю и служебные запросы диагностики, иначе счётчики «плыли» бы
 * от собственной диагностики.
 *
 * <p>В счётчики идёт исходная точность {@code System.nanoTime()} без
 * промежуточного деления на миллисекунды: округление до целых миллисекунд
 * перед сложением обнуляло бы всё, что быстрее миллисекунды, и среднее по
 * сотне запросов показывало бы почти нули. В миллисекундах пересчитывается
 * только порог алерта, где важна читаемость числа, а не точность.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DbQueryMetricsAspect {

    private static final long NANOS_PER_MS = 1_000_000L;

    private final DbQueryMetrics metrics;
    private final DbAlertEvaluator alerts;
    private final DbMetricsProperties properties;

    @Around("execution(public * com.workspace.storm.event.db.repository..*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }
        String call = callName(joinPoint.getSignature());
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsedNanos = System.nanoTime() - startedAt;
            metrics.recordSuccess(call, elapsedNanos, rowsOf(result));
            alerts.checkBudget(call, elapsedNanos / NANOS_PER_MS);
            return result;
        } catch (Throwable e) {
            metrics.recordFailure(call, System.nanoTime() - startedAt, e.getClass().getSimpleName());
            alerts.checkError(call, e.getClass().getSimpleName());
            throw e;
        }
    }

    private static String callName(Signature signature) {
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /**
     * Строки результата: коллекции считаем по размеру, {@code int} из
     * {@code update}/{@code delete} - как число затронутых строк. Всё
     * остальное (void, entity) в счётчик строк не идёт.
     */
    private static int rowsOf(Object result) {
        return switch (result) {
            case null -> 0;
            case Collection<?> collection -> collection.size();
            case Integer rows -> Math.max(0, rows);
            case Long rows -> (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rows));
            default -> 0;
        };
    }
}