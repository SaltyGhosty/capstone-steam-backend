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
 * Pool di thread PROPRIO per l'email: un SMTP lento (Gmail può impiegare secondi) non
 * occupa mai i thread di Tomcat né quelli di altri task @Async.
 *
 * <p>Saturazione: con i thread occupati e la coda piena, CallerRunsPolicy esegue l'invio nel
 * thread che ha pubblicato l'evento (già dopo il commit). È più lento, ma nessuna email va persa.
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
        // allo spegnimento dell'app si finisce di inviare le email in coda (max 30 s)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * Rete di sicurezza per i metodi @Async void: senza questo, un'eccezione andrebbe persa in silenzio.
     * Si registra solo il tipo di ogni parametro: gli eventi email contengono indirizzi email.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Error en tarea asíncrona {}({})", method.getName(),
                Arrays.stream(params).map(p -> p == null ? "null" : p.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")), ex);
    }
}
