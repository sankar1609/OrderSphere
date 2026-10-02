package com.ordersphere.dummygateway;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** The hosted checkout page the customer's browser is redirected to. */
@RestController
public class CheckoutPageController {

  private final CheckoutService checkoutService;

  public CheckoutPageController(CheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @GetMapping(value = "/checkout/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> show(@PathVariable String sessionId) {
    return checkoutService
        .find(sessionId)
        .map(session -> page(session, null, HttpStatus.OK))
        .orElseGet(CheckoutPageController::notFound);
  }

  @PostMapping(value = "/checkout/{sessionId}/pay", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> pay(
      @PathVariable String sessionId,
      @RequestParam(required = false) String cardNumber,
      @RequestParam(required = false) String expiry,
      @RequestParam(required = false) String cvc,
      @RequestParam(required = false) String name) {
    return checkoutService
        .find(sessionId)
        .map(
            session -> {
              CheckoutService.PayResult result =
                  checkoutService.pay(session, cardNumber, expiry, cvc, name);
              return switch (result.outcome()) {
                case SUCCEEDED -> redirect(session.getSuccessUrl());
                case REJECTED -> page(session, result.message(), HttpStatus.PAYMENT_REQUIRED);
                case NOT_PAYABLE -> page(session, null, HttpStatus.CONFLICT);
              };
            })
        .orElseGet(CheckoutPageController::notFound);
  }

  @PostMapping(value = "/checkout/{sessionId}/cancel", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> cancel(@PathVariable String sessionId) {
    return checkoutService
        .find(sessionId)
        .map(
            session ->
                checkoutService.cancel(session)
                    ? redirect(session.getCancelUrl())
                    : page(session, null, HttpStatus.CONFLICT))
        .orElseGet(CheckoutPageController::notFound);
  }

  private static ResponseEntity<String> redirect(String url) {
    return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(url)).build();
  }

  private static ResponseEntity<String> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.TEXT_HTML)
        .body(layout("Checkout not found", "<p>This payment link is invalid.</p>"));
  }

  private ResponseEntity<String> page(CheckoutSession session, String error, HttpStatus status) {
    String amount = esc(session.getAmount().toPlainString() + " " + session.getCurrency());
    String description =
        session.getDescription() == null
            ? ""
            : "<p class=muted>" + esc(session.getDescription()) + "</p>";
    String body =
        switch (checkoutService.status(session)) {
          case OPEN ->
              """
              <div class=amount>%s</div>%s
              %s
              <form method=post action="/checkout/%s/pay">
                <label>Name on card<input name=name autocomplete=cc-name required></label>
                <label>Card number<input name=cardNumber inputmode=numeric autocomplete=cc-number placeholder="4242 4242 4242 4242" required></label>
                <div class=row>
                  <label>Expiry<input name=expiry placeholder="MM/YY" autocomplete=cc-exp required></label>
                  <label>CVC<input name=cvc inputmode=numeric autocomplete=cc-csc placeholder="123" required></label>
                </div>
                <button type=submit>Pay %s</button>
              </form>
              <form method=post action="/checkout/%s/cancel">
                <button type=submit class=link>Cancel and return to store</button>
              </form>
              <div class=hint><b>Test cards</b> (any name, future expiry, any CVC)<br>
                4242 4242 4242 4242 &mdash; succeeds<br>4000 0000 0000 0002 &mdash; declined</div>
              """
                  .formatted(
                      amount,
                      description,
                      error == null ? "" : "<p class=error role=alert>" + esc(error) + "</p>",
                      esc(session.getId()),
                      amount,
                      esc(session.getId()));
          case SUCCEEDED -> finished("This payment is complete.", session.getSuccessUrl());
          case CANCELLED -> finished("This payment was cancelled.", session.getCancelUrl());
          case EXPIRED ->
              finished(
                  "This payment link has expired. Return to the store to check your order.",
                  session.getCancelUrl());
        };
    return ResponseEntity.status(status)
        .contentType(MediaType.TEXT_HTML)
        .body(layout("Pay OrderSphere", body));
  }

  private static String finished(String message, String returnUrl) {
    return "<p>"
        + esc(message)
        + "</p><p><a href=\""
        + esc(returnUrl)
        + "\">Return to store</a></p>";
  }

  private static String layout(String title, String body) {
    return """
        <!doctype html>
        <html lang=en><head><meta charset=utf-8>
        <meta name=viewport content="width=device-width, initial-scale=1">
        <title>%s</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#f4f5f7;margin:0;color:#1d2330}
          main{max-width:400px;margin:48px auto;background:#fff;padding:28px;border-radius:10px;
               box-shadow:0 2px 12px rgba(0,0,0,.08)}
          .brand{font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:#6b7280}
          .amount{font-size:32px;font-weight:600;margin:6px 0 4px}
          .muted{color:#6b7280;margin-top:0}
          label{display:block;font-size:14px;margin:14px 0 0}
          input{display:block;width:100%%;box-sizing:border-box;padding:10px;margin-top:4px;
                border:1px solid #cfd4dc;border-radius:6px;font-size:16px}
          .row{display:flex;gap:12px}.row label{flex:1}
          button{width:100%%;margin-top:20px;padding:12px;border:0;border-radius:6px;
                 background:#3056d3;color:#fff;font-size:16px;cursor:pointer}
          button.link{background:none;color:#3056d3;margin-top:8px}
          .error{background:#fdecec;color:#a61b1b;padding:10px;border-radius:6px}
          .hint{margin-top:20px;font-size:13px;color:#6b7280;background:#f4f5f7;padding:10px;
                border-radius:6px;line-height:1.6}
        </style></head>
        <body><main><div class=brand>Dummy Payment Gateway &middot; Test mode</div>%s</main></body></html>
        """
        .formatted(esc(title), body);
  }

  private static String esc(String value) {
    return HtmlUtils.htmlEscape(value == null ? "" : value);
  }
}
