import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import java.util.UUID;
public class LogEvent implements RequestHandler<SNSEvent, Object> {
    static DynamoDB dynamoDB;
    public Object handleRequest(SNSEvent request, Context context){

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());
        String domain = System.getenv("Domain");
        context.getLogger().log("Domain : " + domain);

        final String FROM = "no-reply@"+domain;
        // Replace recipient@example.com with a "To" address. If your account
        // is still in the sandbox, this address must be verified.
        final String TO = request.getRecords().get(0).getSNS().getMessage();

        try {

            context.getLogger().log("trying to connect to dynamodb");
            init();
            Table table = dynamoDB.getTable("csye6225");
            long unixTime = Instant.now().getEpochSecond()+3*60;
            long now = Instant.now().getEpochSecond();
            context.getLogger().log(unixTime + " calculated time");
            context.getLogger().log(Instant.now().getEpochSecond() + " current time ");
            if(table == null) {
                context.getLogger().log("table not found");
            }
            else{
                Item item = table.getItem("id", request.getRecords().get(0).getSNS().getMessage());
                if(item==null) {
                    String token = UUID.randomUUID().toString();
                    Item itemPut = new Item()
                            .withPrimaryKey("id", request.getRecords().get(0).getSNS().getMessage())//string id
                            .withString("token", token)
                            .withNumber("passwordTokenExpiry", unixTime);

                    context.getLogger().log("AWS request ID:"+context.getAwsRequestId());

                    table.putItem(itemPut);

                    context.getLogger().log("AWS message ID:"+request.getRecords().get(0).getSNS().getMessageId());
                    AmazonSimpleEmailService client =
                            AmazonSimpleEmailServiceClientBuilder.standard()
                                    .withRegion(Regions.US_EAST_1).build();
                    SendEmailRequest req = new SendEmailRequest()
                            .withDestination(
                                    new Destination()
                                            .withToAddresses(TO))
                            .withMessage(
                                    new Message()
                                            .withBody(
                                                    new Body()
                                                            .withHtml(
                                                                    new Content()
                                                                            .withCharset(
                                                                                    "UTF-8")
                                                                            .withData(
                                                                                    "Please click on the below link to reset the password<br/>"+
                                                                                            "<p><a href='#'>http://"+domain+"/reset?email="+TO+"&token="+token+"</a></p>"))
                                            )
                                            .withSubject(
                                                    new Content().withCharset("UTF-8")
                                                            .withData("Password Reset Link")))
                            .withSource(FROM);
                    SendEmailResult response = client.sendEmail(req);
                    System.out.println("Email sent!");
                }
                else if((Long)item.get("passwordTokenExpiry")<=now){
                    context.getLogger().log(item.get("passwordTokenExpiry").toString());
                    context.getLogger().log(item.toJSON() + "Email Already sent!");
                }
                else {
                    context.getLogger().log(item.toJSON() + "Email Already sent!");
                }
            }
        } catch (Exception ex) {
            context.getLogger().log ("The email was not sent. Error message: "
                    + ex.getMessage());
        }


        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        return null;
    }

    private static void init() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        dynamoDB = new DynamoDB(client);
    }
}