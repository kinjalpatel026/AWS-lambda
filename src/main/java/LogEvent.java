import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
            long unixTime = Instant.now().getEpochSecond()+2*60;
            if(table == null)
            {
                context.getLogger().log("table not found");
            }
            else{
                Item item = table.getItem("id", request.getRecords().get(0).getSNS().getMessage());
                if(item==null) {
                    String emailToken = checkToken(request.getRecords().get(0).getSNS().getMessage(), context.getLogger());
                    if (request.getRecords().get(0).getSNS().getMessage() == null) {
                        throw new NullPointerException("Couldn't parse the input email address");
                    }
                    if (emailToken == null) {
                        emailToken = generateNewToken(request.getRecords().get(0).getSNS().getMessage(), context.getLogger());
                        this.sendingResettingEmail(request.getRecords().get(0).getSNS().getMessage(), emailToken, context.getLogger());
                    }

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

    public void sendingResettingEmail(String emailAddress, String token, LambdaLogger logger) throws Exception {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

        String domain = System.getenv("Domain");


        final String FROM = "no-reply@"+domain;
        // Replace recipient@example.com with a "To" address. If your account
        // is still in the sandbox, this address must be verified.
        final String TO = "arunachalam.m@husky.neu.edu";
        if (emailAddress == null || emailAddress.equals("")) {
            throw new IllegalArgumentException("Email Address is not set yet.");
        }
        AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                .withRegion(Regions.US_EAST_1).build();

        String body = "Password reset link already sent";

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

        System.out.println("Email sent successfully!");
    }


    @SuppressWarnings("unused")
    public String checkToken(String email, LambdaLogger logger) {
        String tableName = "password_Reset";

        Table table = dynamoDB.getTable(tableName);
        //try {
        Item item = table.getItem("email", email, "email, reset_token", null);
        if (item == null) {
            return null;
        } else {
            logger.log(item.getString("reset_token"));
        }
        return item.getString("reset_token");
    }

    public String generateNewToken(String emailAddress, LambdaLogger logger) {
        String tableName = "csye6225";
        Table table = dynamoDB.getTable(tableName);
        try {
            String token = randomToken();
            Calendar cal = Calendar.getInstance(); //current date and time
            cal.add(Calendar.MINUTE, 20); //add days
            double ttl =  (cal.getTimeInMillis() / 1000L);
            Item item = new Item().withPrimaryKey("email", emailAddress).withString("reset_token", token)
                    .withDouble("ttl", ttl);
            table.putItem(item);
            return token;
        } catch (Exception e) {
            logger.log(e.toString());
        }
        return null;

    }

    public static String randomToken() {
        String uuid = UUID.randomUUID().toString();
        return uuid;
    }


    private static void init() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        dynamoDB = new DynamoDB(client);
    }
}