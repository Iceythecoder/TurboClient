TURBO ADMIN - PRIVATE. DO NOT SEND THIS FOLDER TO FRIENDS.

Start Turbo Admin.cmd on this PC, under the same Windows account used for setup.
It starts a local admin dashboard and a separate Cloudflare reporting tunnel.
The dashboard opens in your browser. Use the administrator password you supplied.
Its launch credential is protected by Windows DPAPI for this account and PC.
The password is stored as a salted PBKDF2-SHA256 hash (600,000 iterations).
The private folder has restricted Windows permissions. Local administrators or
malware with access to your Windows account can still compromise local software.

The admin dashboard listens only on 127.0.0.1:8764. That is its IP restriction:
remote computers cannot access it, even if they know the reporting URL/password.
Cloudflare forwards only to the separate collector on 127.0.0.1:8765.
No router port forwarding is required. Never tunnel the admin port.

The public HTTPS address is written to usage-endpoint.properties here and next
to your Turbo Client.exe. Send ONLY that file and the clean friends ZIP.
Friends put the file next to Turbo Client.exe, reopen the client, and use the
Usage privacy menu to opt in. Username reporting requires separate permission.
Both options default off. A changed reporting address requires new consent.

Quick Tunnel addresses change on restart and have no uptime guarantee. Keep
the admin launcher running while monitoring. After restarting it, distribute
the new usage-endpoint.properties. A named tunnel/domain is needed for a
permanent address. Clients remain fully functional if reporting is unavailable.

The panel displays active sessions, not total installations or verified people.
A session expires after 120 seconds without a heartbeat. Usernames and active
sessions exist only in memory and are cleared when the service stops. Browser
password fields are cleared on submission; no browser local storage is used.
Turbo stores no user IP addresses or request logs. Cloudflare and network
providers necessarily handle connection IPs and may retain their own records.

Only preference booleans and the consented endpoint are saved on a friend's PC.
An anonymous random session ID is regenerated per application runtime. No HWID,
PC name, account password, location, or inventory data is reported. Heartbeats
are sent about every 30 seconds. Withdrawing consent sends a removal request;
if offline, its previous report expires within two minutes.

The report credential in the shared connection file is not an admin credential.
Recipients can inspect or simulate reports, so counts are approximate and not
a licensing, anti-abuse, or identity-verification system. Copying this PRIVATE
admin folder or its contents into the friends ZIP is prohibited by this design.
