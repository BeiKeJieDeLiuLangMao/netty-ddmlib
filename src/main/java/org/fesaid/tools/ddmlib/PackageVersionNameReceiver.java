package org.fesaid.tools.ddmlib;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * @author Chen Yang/CL10060-N/chen.yang@linecorp.com
 */
public class PackageVersionNameReceiver extends MultiLineReceiver {

    private Pattern p1;
    private Pattern p2;

    @Getter
    private String versionName;

    public PackageVersionNameReceiver(String packageName) {
        this.p1 = Pattern.compile("^\\s*Package\\s+\\[" + packageName + "\\].*$");
        this.p2 = Pattern.compile("^\\s*versionName=(.*)$");
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    private boolean foundPackage = false;
    private boolean foundVersion = false;

    @Override
    public void processNewLines(String[] lines) {

        if (foundVersion) {
            return;
        }

        for (String s : lines) {
            if (!this.foundPackage) {
                if (p1.matcher(s).matches()) {
                    foundPackage = true;
                }
                continue;
            }

            Matcher m = p2.matcher(s);
            if (m.matches()) {
                versionName = m.group(1);
                foundVersion = true;
                return;
            }
        }
    }

}

