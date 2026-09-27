# Notification Templates

Source of truth: `src/main/java/ameso/notificationservice/Templates.java`.
Sent automatically when a care executive ACKs an incident (Kafka `IncidentAcknowledged`).

## WhatsApp delivery

Emergency contacts whose `preferred_channel` is WhatsApp, and the ambulance
dispatch message, go through `WhatsAppCloudApiProvider` (Meta WhatsApp Cloud
API). Configure real delivery via:

```
WhatsApp__AccessToken=<Meta App access token>
WhatsApp__PhoneNumberId=<sending number's Phone Number ID>
```

Without these set, the provider logs the message and simulates delivery —
the full workflow (dispatch, audit, dashboard status) runs identically either
way. A production rollout needs Meta-approved message templates for
business-initiated conversations outside the 24-hour customer-service
window; this POC sends free-form text, which works against Meta's test
numbers and within an open session.

## Hospital — Email

Subject: `Emergency Patient Alert`

```
EMERGENCY ALERT

Patient:
John Doe

Age:
76

Blood Group:
O+

Current Location:
https://maps.google.com/?q=55.6761,12.5683

Medical Conditions:
Diabetes
Hypertension

Emergency Contact:
+45 12345678

Incident ID:
INC-202607270001

Please dispatch emergency assistance immediately.
```

## Emergency Contact — WhatsApp (sent on ACK)

```
Patient John Doe is being attended by Gleneagles BGS Kengeri Emergency Providers.
```

## Emergency Contact — Email

```
EMERGENCY ALERT

John Doe has activated an emergency SOS.

Location:
https://maps.google.com/?q=55.6761,12.5683

Hermes Customer Care has acknowledged the emergency and is coordinating assistance.

Incident ID:
INC-202607270001
```

## Ambulance Dispatch — WhatsApp to hospital (sent on "Call Ambulance")

```
AMBULANCE DISPATCH REQUEST

Patient:
John Doe

Location (lat, long):
12.971599, 77.594566
https://maps.google.com/?q=12.971599,77.594566

Emergency Contacts:
Priya Patel — +4512345679
Daksh Sharma — +4512345680

Incident ID:
INC-202607270001

Please dispatch an ambulance immediately.
```

## Emergency Contact — SMS

```
Emergency Alert

John Doe activated SOS.

Location:
https://maps.google.com/?q=55.6761,12.5683

Hermes is coordinating assistance.
```

## Channel routing

Each emergency contact has a `preferred_channel` (SMS | WhatsApp | Email).
The hospital always receives Email. Every send is recorded in the
`notification` table with status Pending → Sent → Delivered / Failed.
