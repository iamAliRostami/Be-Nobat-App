FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY . .
RUN dotnet restore BeNobat.slnx /p:TreatWarningsAsErrors=false && dotnet publish src/BeNobat.Web/BeNobat.Web.csproj -c Release -o /app --no-restore /p:TreatWarningsAsErrors=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app .
# پوشه‌ی کلیدهای Data Protection (کوکی ورود و antiforgery) تا با ری‌استارت کانتینر باطل نشوند.
RUN mkdir -p /keys && chown -R $APP_UID:0 /keys
USER $APP_UID
EXPOSE 8080
ENTRYPOINT ["dotnet", "BeNobat.Web.dll"]
