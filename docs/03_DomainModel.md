\# Domain Model



| Document | Domain Model |

|----------|--------------|

| Project | Be Nobat |

| Version | 1.0 |

| Status | Draft |



\---



\# Purpose



هدف این سند تعریف مدل دامنه (Domain Model) سیستم «به نوبت» است.



این سند مرجع اصلی طراحی Backend، Database، API و Business Logic خواهد بود.



هیچ جدول دیتابیس یا API نباید خارج از قوانین این سند طراحی شود.



\---



\# Design Principles



مدل دامنه بر اساس اصول زیر طراحی شده است.



\- Domain Driven Design (DDD)

\- Clean Architecture

\- SOLID

\- Soft Delete

\- Audit First

\- Configuration over Hardcoding

\- Multi Tenant

\- High Scalability

\- Event Driven Ready



\---



\# Bounded Contexts



سیستم از چند Domain مستقل تشکیل شده است.



Identity



Business



Catalog



Scheduling



Booking



Customer



Notification



System



هر Context مسئول داده‌ها و قوانین خودش است.



هیچ Context نباید مستقیماً منطق داخلی Context دیگر را تغییر دهد.



