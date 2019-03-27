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
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

public class LogEvent implements RequestHandler<SNSEvent, Object> {
    static DynamoDB dynamoDB;
    public Object handleRequest(SNSEvent request, Context context){

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());

        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        final String FROM = "kinjal@csye6225-spring2019-kuvalekars.me";

        // Replace recipient@example.com with a "To" address. If your account
        // is still in the sandbox, this address must be verified.
        final String TO = "kuvalekar.s@husky.neu.edu";


        // The configuration set to use for this email. If you do not want to use a
        // configuration set, comment the following variable and the
        // .withConfigurationSetName(CONFIGSET); argument below.

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
            Table table = dynamoDB.getTable("csye-6225");
//            long unixTime = Instant.now().getEpochSecond()+20*60;
            if(table == null)
            {
                context.getLogger().log("table is culprit");
            }
            else{
                Item item = table.getItem("id", request.getRecords().get(0).getSNS().getMessage());
                if(item==null) {
                    Item itemPut = new Item()
                            .withPrimaryKey("id", request.getRecords().get(0).getSNS().getMessage());

                    table.putItem(itemPut);
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

        try {
            AmazonSimpleEmailService client =
                    AmazonSimpleEmailServiceClientBuilder.standard()
                            // Replace US_WEST_2 with the AWS Region you're using for
                            // Amazon SES.
                            .withRegion(Regions.US_EAST_1).build();
            SendEmailRequest emailrequest = new SendEmailRequest()
                    .withDestination(
                            new Destination().withToAddresses(TO))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8").withData(HTMLBODY))
                                    .withText(new Content()
                                            .withCharset("UTF-8").withData(TEXTBODY)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8").withData(SUBJECT)))
                    .withSource(FROM);

            // Comment or remove the next line if you are not using a
            // configuration set
            //.withConfigurationSetName(CONFIGSET);
            client.sendEmail(emailrequest);
            System.out.println("Email sent!");
        } catch (Exception ex) {
            System.out.println("The email was not sent. Error message: "
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