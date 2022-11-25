package geektime.spring.springbucks;

import com.github.pagehelper.PageInfo;
import geektime.spring.springbucks.mapper.CoffeeMapper;
import geektime.spring.springbucks.model.Coffee;
import geektime.spring.springbucks.model.CoffeeOrder;
import geektime.spring.springbucks.model.OrderState;
import geektime.spring.springbucks.repository.CoffeeRepository;
import geektime.spring.springbucks.service.CoffeeOrderService;
import geektime.spring.springbucks.service.CoffeeService;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers.exact;

@Slf4j
@EnableTransactionManagement
@SpringBootApplication
@EnableJpaRepositories
@MapperScan("geektime.spring.springbucks.mapper")
public class SpringBucksApplication implements ApplicationRunner {
	private static final String CACHE = "springbucks-coffeeList";
	@Autowired
	private CoffeeRepository coffeeRepository;
	@Autowired
	private CoffeeService coffeeService;
	@Autowired
	private CoffeeOrderService orderService;
	@Autowired
	private CoffeeMapper coffeeMapper;
	@Autowired
	private RedisTemplate<String, Coffee> redisTemplate;

	public static void main(String[] args) {
		SpringApplication.run(SpringBucksApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("All Coffee: {}", coffeeRepository.findAll());

		Optional<Coffee> latte = coffeeService.findOneCoffee("Latte");
		if (latte.isPresent()) {
			CoffeeOrder order = orderService.createOrder("Li Lei", latte.get());
			log.info("Update INIT to PAID: {}", orderService.updateState(order, OrderState.PAID));
			log.info("Update PAID to INIT: {}", orderService.updateState(order, OrderState.INIT));
		}
		// 分页查询
		List<Coffee> list = coffeeMapper.findAllWithParam(1, 10);
		String name = "1-10";
		PageInfo page = new PageInfo(list);
		HashOperations<String, String, Coffee> hashOperations = redisTemplate.opsForHash();
		if (redisTemplate.hasKey(CACHE) && hashOperations.hasKey(CACHE, name)) {
			log.info("Get coffee {} from Redis.", name);
		}
		ExampleMatcher matcher = ExampleMatcher.matching()
				.withMatcher("name", exact().ignoreCase());
		Optional<Coffee> coffee = coffeeRepository.findOne(
				Example.of(Coffee.builder().name(name).build(), matcher));
		log.info("Coffee Found: {}", coffee);
		if (coffee.isPresent()) {
			log.info("Put coffee {} to Redis.", name);
			hashOperations.put(CACHE, name, coffee.get());
			redisTemplate.expire(CACHE, 1, TimeUnit.MINUTES);
		}
		log.info("PageInfo: {}", page);
	}
}

