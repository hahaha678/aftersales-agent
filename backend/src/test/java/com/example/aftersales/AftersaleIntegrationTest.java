package com.example.aftersales;

import com.example.aftersales.identity.domain.dto.CreateSessionDTO;
import com.example.aftersales.identity.service.SessionService;
import com.example.aftersales.identity.security.TokenCodec;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AUTH_TEST_ENABLED",matches="true")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${MAPPER_TEST_URL}","spring.datasource.username=${MAPPER_TEST_USERNAME}",
 "spring.datasource.password=${MAPPER_TEST_PASSWORD}","spring.data.redis.port=${REDIS_TEST_PORT}",
 "app.auth.cache.namespace=aftersales-business-it"
})
class AftersaleIntegrationTest {
 @Value("${local.server.port}") int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired SessionService auth;
 @Autowired StringRedisTemplate redis;
 final JsonMapper json=JsonMapper.builder().build();
 final List<Long> users=new ArrayList<>();
 final List<String> tokens=new ArrayList<>();
 long order,item,expired,foreign,staffOrder,staffItem;
 @BeforeEach void fixture(){
  for(String role:List.of("CUSTOMER","CUSTOMER","STAFF")){
   String name="after_"+UUID.randomUUID().toString().replace("-","");
   jdbc.update("INSERT INTO app_user(username,password_hash,display_name,role) VALUES(?,?,?,?)",name,"{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80","售后测试",role);
   users.add(jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,name));
   tokens.add(auth.login(new CreateSessionDTO(name,"DemoPass123!")).accessToken());
  }
  order=makeOrder(users.get(0),2);expired=makeOrder(users.get(0),8);foreign=makeOrder(users.get(1),2);staffOrder=makeOrder(users.get(2),2);
  item=itemOf(order);staffItem=itemOf(staffOrder);
 }
 long makeOrder(long user,int age){
  String number="AFTER-"+UUID.randomUUID();
  jdbc.update("INSERT INTO trade_order(user_id,order_number,status,paid_amount,created_at,paid_at,signed_at) VALUES(?,?,'COMPLETED',10.00,UTC_TIMESTAMP()-INTERVAL 15 DAY,UTC_TIMESTAMP()-INTERVAL 14 DAY,DATE_SUB(UTC_TIMESTAMP(),INTERVAL ? DAY))",user,number,age);
  long id=jdbc.queryForObject("SELECT id FROM trade_order WHERE order_number=?",Long.class,number);
  jdbc.update("INSERT INTO order_item(order_id,sku_id,product_name,quantity,paid_amount) VALUES(?,101,'三件分摊商品',3,10.00)",id);
  return id;
 }
 long itemOf(long oid){return jdbc.queryForObject("SELECT id FROM order_item WHERE order_id=?",Long.class,oid);}
 @AfterEach void cleanup(){
  for(long uid:users){
   jdbc.update("DELETE e FROM aftersale_event e JOIN aftersale_request a ON a.id=e.request_id WHERE a.user_id=?",uid);
   jdbc.update("DELETE FROM aftersale_request WHERE user_id=?",uid);
   jdbc.update("DELETE i FROM order_item i JOIN trade_order o ON o.id=i.order_id WHERE o.user_id=?",uid);
   jdbc.update("DELETE FROM trade_order WHERE user_id=?",uid);
   jdbc.update("DELETE FROM user_session WHERE user_id=?",uid);
   jdbc.update("DELETE FROM app_user WHERE id=?",uid);
  }
  for(String token:tokens){String prefix="aftersales-business-it:{"+TokenCodec.hash(token)+"}:";redis.delete(List.of(prefix+"session",prefix+"revoked"));}
 }
 HttpResponse<String> call(String method,String path,Object body,int actor) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path));
  if(actor>=0)builder.header("Authorization","Bearer "+tokens.get(actor));
  builder.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
  try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
 }
 Map<String,Object> body(long oid,long iid,int qty,String key){return Map.of("orderId",String.valueOf(oid),"orderItemId",String.valueOf(iid),"quantity",qty,"reason","QUALITY","description","商品有损坏","requestKey",key);}
 JsonNode expect(HttpResponse<String> response,int code){assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(code);return json.readTree(response.body());}
 String create(int qty) throws Exception {return expect(call("POST","/aftersales",body(order,item,qty,UUID.randomUUID().toString()),0),201).path("id").asString();}
 int available() throws Exception {return expect(call("GET","/orders/"+order+"/aftersale-eligibility",null,0),200).path("items").get(0).path("availableQuantity").asInt();}
 @Test void ownershipRolesAndInputValidation() throws Exception {
  expect(call("GET","/aftersales",null,-1),401);
  expect(call("GET","/orders/"+foreign+"/aftersale-eligibility",null,0),404);
  expect(call("GET","/staff/aftersales",null,0),403);
  String id=create(1);
  expect(call("GET","/aftersales/"+id,null,1),404);
  expect(call("POST","/aftersales/"+id+"/cancellation",null,1),404);
  expect(call("POST","/staff/aftersales/"+id+"/review",Map.of("decision","APPROVED","note","同意"),0),403);
  expect(call("POST","/aftersales",body(order,item,0,UUID.randomUUID().toString()),0),400);
  expect(call("POST","/aftersales",body(order,itemOf(foreign),1,UUID.randomUUID().toString()),0),404);
  expect(call("GET","/aftersales?page=0",null,0),400);
  expect(call("GET","/aftersales?status=UNKNOWN",null,0),400);
  expect(call("GET","/aftersales/9999999999999999999",null,0),400);
 }
 @Test void eligibilityExpiryStateAndZeroAmount() throws Exception {
  var result=expect(call("GET","/orders/"+expired+"/aftersale-eligibility",null,0),200);
  assertThat(result.path("items").get(0).path("eligible").asBoolean()).isFalse();
  expect(call("POST","/aftersales",body(expired,itemOf(expired),1,UUID.randomUUID().toString()),0),409);
  jdbc.update("UPDATE trade_order SET status='SHIPPED',signed_at=NULL WHERE id=?",order);
  expect(call("POST","/aftersales",body(order,item,1,UUID.randomUUID().toString()),0),409);
  jdbc.update("UPDATE trade_order SET status='COMPLETED',signed_at=UTC_TIMESTAMP()-INTERVAL 1 DAY WHERE id=?",order);
  jdbc.update("UPDATE order_item SET paid_amount=0 WHERE id=?",item);
  expect(call("POST","/aftersales",body(order,item,1,UUID.randomUUID().toString()),0),409);
 }
 @Test void idempotencyCancellationAndQuantityRelease() throws Exception {
  var body=body(order,item,2,UUID.randomUUID().toString());
  var first=expect(call("POST","/aftersales",body,0),201);
  var again=expect(call("POST","/aftersales",body,0),201);
  assertThat(again.path("id").asString()).isEqualTo(first.path("id").asString());
  assertThat(again.path("events").size()).isEqualTo(1);assertThat(available()).isEqualTo(1);
  expect(call("POST","/aftersales",body(order,item,1,(String)body.get("requestKey")),0),409);
  String id=first.path("id").asString();
  expect(call("POST","/aftersales/"+id+"/cancellation",null,0),200);
  var cancelled=expect(call("POST","/aftersales/"+id+"/cancellation",null,0),200);
  assertThat(cancelled.path("events").size()).isEqualTo(2);assertThat(available()).isEqualTo(3);
  assertThat(expect(call("POST","/aftersales",body,0),201).path("status").asString()).isEqualTo("CANCELLED");
  var details=expect(call("GET","/orders/"+order,null,0),200);
  assertThat(details.path("items").get(0).path("availableAftersalesQuantity").asInt()).isEqualTo(3);
 }
 @Test void reviewAuditAndRoundingNeverOverRefund() throws Exception {
  String a=create(1),b=create(2);
  assertThat(available()).isZero();
  assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM aftersale_request WHERE order_id=?",java.math.BigDecimal.class,order)).isEqualByComparingTo("10.00");
  var approve=Map.of("decision","APPROVED","note","同意申请，请等待退货指引");
  var approved=expect(call("POST","/staff/aftersales/"+a+"/review",approve,2),200);
  assertThat(approved.path("status").asString()).isEqualTo("APPROVED");
  assertThat(expect(call("POST","/staff/aftersales/"+a+"/review",approve,2),200).path("events").size()).isEqualTo(2);
  expect(call("POST","/aftersales/"+a+"/cancellation",null,0),409);
  expect(call("POST","/staff/aftersales/"+a+"/review",Map.of("decision","REJECTED","note","变更"),2),409);
  expect(call("POST","/staff/aftersales/"+b+"/review",Map.of("decision","REJECTED","note","凭据不足"),2),200);
  assertThat(available()).isEqualTo(2);
  String replacement=create(2);
  assertThat(expect(call("GET","/aftersales/"+replacement,null,0),200).path("amount").asString()).isEqualTo("6.67");
 }
 @Test void staffCannotReviewOwnApplicationAndListIsScoped() throws Exception {
  create(1);
  String own=expect(call("POST","/aftersales",body(staffOrder,staffItem,1,UUID.randomUUID().toString()),2),201).path("id").asString();
  expect(call("POST","/staff/aftersales/"+own+"/review",Map.of("decision","APPROVED","note","自己审核"),2),403);
  assertThat(expect(call("GET","/aftersales",null,1),200).path("total").asLong()).isZero();
  assertThat(expect(call("GET","/staff/aftersales?status=PENDING",null,2),200).path("total").asLong()).isEqualTo(2);
  assertThat(expect(call("GET","/aftersales?page=999",null,0),200).path("items").size()).isZero();
  assertThat(expect(call("GET","/aftersales?page=999",null,0),200).path("total").asLong()).isEqualTo(1);
 }
 @Test void concurrentRequestsCannotOverAllocate() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   Callable<Integer> submit=()->{gate.await();return call("POST","/aftersales",body(order,item,2,UUID.randomUUID().toString()),0).statusCode();};
   var first=pool.submit(submit);var second=pool.submit(submit);gate.countDown();
   assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
  assertThat(available()).isEqualTo(1);
 }
 @Test void concurrentSameKeyCreatesOneRequest() throws Exception {
  var data=body(order,item,1,UUID.randomUUID().toString());
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   Callable<String> submit=()->{gate.await();return expect(call("POST","/aftersales",data,0),201).path("id").asString();};
   var first=pool.submit(submit);var second=pool.submit(submit);gate.countDown();
   assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
  }
  assertThat(available()).isEqualTo(2);
 }
 @Test void reviewAndCancellationRaceHasOneWinner() throws Exception {
  String id=create(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var gate=new CountDownLatch(1);
   var cancel=pool.submit(()->{gate.await();return call("POST","/aftersales/"+id+"/cancellation",null,0).statusCode();});
   var review=pool.submit(()->{gate.await();return call("POST","/staff/aftersales/"+id+"/review",Map.of("decision","APPROVED","note","审核通过"),2).statusCode();});
   gate.countDown();assertThat(List.of(cancel.get(15,TimeUnit.SECONDS),review.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  assertThat(expect(call("GET","/aftersales/"+id,null,0),200).path("events").size()).isEqualTo(2);
 }
}
