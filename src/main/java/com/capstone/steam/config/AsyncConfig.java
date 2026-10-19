package com.capstone.steam.config;

import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool de hilos PROPIO para el correo: un SMTP lento (Gmail puede tardar segundos) nunca
 * ocupa los hilos de Tomcat ni los de otras tareas @Async.
 *
 * <p>Saturación: con los hilos ocupados y la cola llena, CallerRunsPolicy ejecuta el envío en el
 * hilo que publicó el evento (ya después del commit). Es más lento, pero no se pierde ningún correo.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = MAIL_EXECUTOR)
    public ThreadPoolTaskExecutor mailExecutor(MailExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mail-");
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // al apagar la app se terminan de enviar los correos en cola (máx. 30 s)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * Red de seguridad para métodos @Async void: sin esto, una excepción se perdería en silencio.
     * Solo se registra el tipo de cada parámetro: los eventos de correo llevan emails.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Error en tarea asíncrona {}({})", method.getName(),
                Arrays.stream(params).map(p -> p == null ? "null" : p.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")), ex);
    }
}
