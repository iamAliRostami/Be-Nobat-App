FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY ./publish .
# پوشه‌ی کلیدهای Data Protection (کوکی ورود و antiforgery) تا با ری‌استارت کانتینر باطل نشوند.
RUN mkdir -p /keys && chown -R $APP_UID:0 /keys
USER $APP_UID
EXPOSE 8080
ENTRYPOINT ["dotnet", "BeNobat.Web.dll"]

