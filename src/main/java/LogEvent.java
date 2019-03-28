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

public class LogEvent implements RequestHandler<SNSEvent, Object> {
    static DynamoDB dynamoDB;
    public Object handleRequest(SNSEvent request, Context context){

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());

        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        final String FROM = "kinjal@csye6225-spring2019-patelkin.me";

        // Replace recipient@example.com with a "To" address. If your account
        // is still in the sandbox, this address must be verified.
        String TO = request.getRecords().get(0).getSNS().getMessage();

        // The configuration set to use for this email. If you do not want to use a
        // configuration set, comment the following variable and the
        // .withConfigurationSetName(CONFIGSET); argument below.
        //final String CONFIGSET = "ConfigSet";

        // The subject line for the email.
        final String SUBJECT = "Amazon SES test (AWS SDK for Java)";
        // The HTML body for the email.
        final String HTMLBODY = "<h1>Amazon SES test (AWS SDK for Java)</h1>"
                + "<p>This email was sent with <a href='https://aws.amazon.com/ses/'>"
                + "Amazon SES</a> using the <a href='https://aws.amazon.com/sdk-for-java/'>"
                + "AWS SDK for Java</a>";

        // The email body for recipients with non-HTML email clients.
        final String TEXTBODY = "This email was sent through Amazon SES "
                + "using the AWS SDK for Java.";
        try {
            context.getLogger().log("trying to connect to dynamodb");
            init();
            Table table = dynamoDB.getTable("csye6225");
            long unixTime = Instant.now().getEpochSecond()+20*60;
            if(table == null)
            {
                context.getLogger().log("table is culprit");
            }
            else{
                Item item = table.getItem("id", request.getRecords().get(0).getSNS().getMessage());
                if(item==null) {
                    Item itemPut = new Item()
                            .withPrimaryKey("id", request.getRecords().get(0).getSNS().getMessage())//string id
                            .withString("token", context.getAwsRequestId())
                            .withNumber("passwordTokenExpiry", unixTime);


                    table.putItem(itemPut);

                    String token = request.getRecords().get(0).getSNS().getMessageId();
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
                                                                                            "<p><a href='#'>https://"+FROM+"/resetPwd?email="+TO+"&token="+token+"</a></p>"))
                                            )
                                            .withSubject(
                                                    new Content().withCharset("UTF-8")
                                                            .withData("Password Reset Link")))
                            .withSource(FROM);
                    SendEmailResult response = client.sendEmail(req);
                    System.out.println("Email sent!");
                }
                else {
                    context.getLogger().log(item.toJSON() + "Above Item");
                    context.getLogger().log("Above Item");
                }
            }

        } catch (Exception ex) {
            context.getLogger().log ("The email was not sent. Error message: "
                    + ex.getMessage());
        }
        return null;
    }

    private static void init() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        dynamoDB = new DynamoDB(client);
    }
}
