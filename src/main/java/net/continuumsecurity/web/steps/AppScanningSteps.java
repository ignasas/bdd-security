/*******************************************************************************
 *    BDD-Security, application security testing framework
 *
 * Copyright (C) `2014 Stephen de Vries`
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see `<http://www.gnu.org/licenses/>`.
 ******************************************************************************/
package net.continuumsecurity.web.steps;

import net.continuumsecurity.Config;
import net.continuumsecurity.FalsePositive;
import net.continuumsecurity.UnexpectedContentException;
import net.continuumsecurity.Utils;
import net.continuumsecurity.proxy.ScanningProxy;
import net.continuumsecurity.proxy.Spider;
import net.continuumsecurity.web.Application;
import net.continuumsecurity.web.drivers.ProxyFactory;

import org.apache.log4j.Logger;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Named;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.jbehave.core.model.ExamplesTable;
import org.junit.internal.runners.statements.Fail;
import org.zaproxy.clientapi.core.Alert;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class AppScanningSteps {
    Logger log = Logger.getLogger(AppScanningSteps.class);
    ScanningProxy scanner;
    Spider spider;
    Application app;
    List<Alert> alerts = new ArrayList<Alert>();
    String scannerIds = null;

    public AppScanningSteps() {

    }


    @Given("a new scanning session")
    public void createNewScanSession() {
        app = Config.createApp();
        app.enableHttpLoggingClient();
        scanner = ProxyFactory.getScanningProxy();
        spider = ProxyFactory.getSpider();
        scanner.clear();
        alerts.clear();
    }

    @Given("a scanner with all policies disabled")
    public void disableAllScanners() {
        scanner.disableAllScanners();
    }

    @Given("the page flow described in the method: $methodName is run through the proxy")
    public void navigateApp(@Named("methodName") String methodName) throws Exception {
        Method method = app.getClass().getMethod(methodName);
        app.enableHttpLoggingClient();
        log.debug("Navigating method: " + method.getName());
        method.invoke(app);
    }

    @Given("the following URLs are spidered: $urlsTable")
    public void spiderUrls(ExamplesTable urlsTable) throws InterruptedException {
        for (Map<String, String> row : urlsTable.getRows()) {
            String url = row.get("url");
            if (url.equalsIgnoreCase("baseurl")) url = Config.getBaseUrl();
            else if (url.equalsIgnoreCase("basesecureurl")) url = Config.getBaseSecureUrl();
            spider(url);
        }
    }

    @Given("the spider is configured for a maximum depth of $depth")
    public void setSpiderDepth(@Named("depth") int depth) {
        spider.setMaxDepth(depth);
    }

    @Given("the following URL regular expressions are excluded from the spider: $excludedUrlsTable")
    public void setExcludedRegex(ExamplesTable exRegex) {
        for (Map<String, String> row : exRegex.getRows()) {
            spider.excludeFromScan(row.get("regex"));
        }
    }


    @Given("the spider is configured for $threads concurrent threads")
    public void setSpiderThreads(@Named("threads") int threads) {
        spider.setThreadCount(threads);
    }


    private void spider(String url) throws InterruptedException {
        spider.spider(url);
        int complete = spider.getSpiderStatus();
        while (complete < 100) {
            complete = spider.getSpiderStatus();
            log.debug("Spidering of: " + url + " is " + complete + "% complete.");
            Thread.sleep(1000);
        }
        for (String result : spider.getSpiderResults()) {
            log.debug("Found Url: " + result);
        }
    }

    @Given("the passive scanner is enabled")
    public void enablePassiveScanner() {
        scanner.setEnablePassiveScan(true);
    }


    @Given("the $policyName policy is enabled")
    public void enablePolicy(@Named("policyName") String policyName) {
        switch (policyName.toLowerCase()) {
            case "directory-browsing":
                scannerIds = "0";
                break;
            case "cross-site-scripting":
                scannerIds = "40012,40014,40016,40017";
                break;
            case "sql-injection":
                scannerIds = "40018";
                break;
            case "path-traversal":
                scannerIds = "6";
                break;
            case "remote-file-inclusion":
                scannerIds = "7";
                break;
            case "server-side-include":
                scannerIds = "40009";
                break;
            case "script-active-scan-rules":
                scannerIds = "50000";
                break;
            case "server-side-code-injection":
                scannerIds = "90019";
                break;
            case "external-redirect":
                scannerIds = "30000";
                break;
            case "crlf-injection":
                scannerIds = "40003";
                break;
        }
        if (scannerIds == null) throw new UnexpectedContentException("No matching policy found for: " + policyName);
        scanner.setEnableScanners(scannerIds, true);
    }

    @Given("the attack strength is set to $strength")
    public void setAttackStrength(String strength) {
        if (scannerIds == null)
            throw new RuntimeException("First set the scanning policy before setting attack strength or alert threshold");
        for (String id : scannerIds.split(",")) {
            scanner.setScannerAttackStrength(id, strength.toUpperCase());
        }
    }

    @Given("the alert threshold is set to $threshold")
    public void setAlertThreshold(String threshold) {
        if (scannerIds == null)
            throw new RuntimeException("First set the scanning policy before setting attack strength or alert threshold");
        for (String id : scannerIds.split(",")) {
            scanner.setScannerAlertThreshold(id, threshold.toUpperCase());
        }
    }

    @When("the scanner is run")
    public void runScanner() throws Exception {
        log.info("Scanning: "+Config.getBaseUrl());
        scanner.scan(Config.getBaseUrl());
        int complete = 0;
        while (complete < 100) {
            complete = scanner.getScanStatus();
            log.debug("Scan is " + complete + "% complete.");
            Thread.sleep(1000);
        }
    }

    @When("false positives described in: $falsePositives are removed")
    public void removeFalsePositives(ExamplesTable falsePositives) {
        alerts = scanner.getAlerts();
        List<Alert> clean = new ArrayList<Alert>();

        for (Alert alert : alerts) {
            boolean falsePositive = false;
            for (FalsePositive falsep : Utils.getFalsePositivesFromTable(falsePositives)) {
                if (falsep.matches(alert.getUrl(), alert.getParam(), Integer.toString(alert.getCweId()))) {
                    falsePositive = true;
                }
            }
            if (!falsePositive && !containsAlertByValue(clean,alert)) {
                clean.add(alert);
            }
        }
        alerts = clean;
    }

    @Then("no $riskRating or higher risk vulnerabilities should be present")
    public void checkVulnerabilities(@Named("riskRating") String risk) {
        List<Alert> filteredAlerts = null;
        Alert.Risk riskLevel = Alert.Risk.High;

        if ("HIGH".equalsIgnoreCase(risk)) {
            riskLevel = Alert.Risk.High;
        } else if ("MEDIUM".equalsIgnoreCase(risk)) {
            riskLevel = Alert.Risk.Medium;
        } else if ("LOW".equalsIgnoreCase(risk)) {
            riskLevel = Alert.Risk.Low;
        }
        filteredAlerts = getAllAlertsByRiskRating(alerts, riskLevel);
        String details = getAlertDetails(filteredAlerts);

        assertThat(filteredAlerts.size() + " " + risk + " vulnerabilities found.\nDetails:\n" + details, filteredAlerts.size(),
                equalTo(0));
    }

    @Given("the spider status reaches 100% complete")
    public void waitForSpiderToComplete() {
        int status = 0;
        while (status < 100) {
            status = spider.getSpiderStatus();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private List<Alert> getAllAlertsByRiskRating(List<Alert> alerts, Alert.Risk rating) {
        List<Alert> results = new ArrayList<Alert>();
        for (Alert alert : alerts) {
            if (alert.getRisk().ordinal() >= rating.ordinal()) results.add(alert);
        }
        return results;
    }

    private String getAlertDetails(List<Alert> alerts) {
        String detail = "";
        if (alerts.size() != 0) {
            for (Alert alert : alerts) {
                detail = detail + alert.getAlert() + "\n"
                        + "URL: " + alert.getUrl() + "\n"
                        + "Parameter: " + alert.getParam() + "\n"
                        + "CWE: " + alert.getCweId() + "\n";
            }
        }
        return detail;
    }

    public boolean alertsMatchByValue(Alert first, Alert second) {
        //The built in Alert.matches(Alert) method includes risk, reliability and alert, but not cweid.
        if (first.getCweId() != second.getCweId()) return false;
        if (!first.getParam().equals(second.getParam())) return false;
        if (!first.getUrl().equals(second.getUrl())) return false;
        if (!first.matches(second)) return false;
        return true;
    }


    public boolean containsAlertByValue(List<Alert> alerts, Alert alert) {
        boolean found = false;
        for (Alert existing : alerts) {
            if (alertsMatchByValue(alert, existing)) {
                found = true;
                break;
            }
        }
        return found;
    }

}
