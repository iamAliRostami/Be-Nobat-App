// Map & Navigation Helper for Be-Nobat (Leaflet + OpenStreetMap / CartoDB + Navigation Apps)
(function () {
    let leafletLoading = null;

    function ensureLeaflet() {
        if (window.L) {
            if (window.L.Icon && window.L.Icon.Default) {
                window.L.Icon.Default.imagePath = '/lib/leaflet/';
            }
            return Promise.resolve(window.L);
        }
        if (leafletLoading) return leafletLoading;

        leafletLoading = new Promise((resolve, reject) => {
            if (!document.querySelector('link[href*="leaflet"]')) {
                const link = document.createElement('link');
                link.rel = 'stylesheet';
                link.href = '/lib/leaflet/leaflet.css';
                document.head.appendChild(link);
            }

            const script = document.createElement('script');
            script.src = '/lib/leaflet/leaflet.js';
            script.onload = () => {
                if (window.L && window.L.Icon && window.L.Icon.Default) {
                    window.L.Icon.Default.imagePath = '/lib/leaflet/';
                }
                resolve(window.L);
            };
            script.onerror = () => reject(new Error('Failed to load Leaflet'));
            document.body.appendChild(script);
        });

        return leafletLoading;
    }

    function createPinIcon(L) {
        return L.divIcon({
            className: 'benobat-custom-pin',
            html: '<div style="font-size:32px;line-height:32px;text-align:center;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.35));cursor:pointer;user-select:none;">📍</div>',
            iconSize: [36, 36],
            iconAnchor: [18, 34]
        });
    }

    function addTileLayer(map, L) {
        // High-speed, high-availability tiles worldwide and in Iran (Google Maps base layer)
        const tileLayer = L.tileLayer('https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
            maxZoom: 20,
            subdomains: ['0', '1', '2', '3'],
            attribution: '© Google Maps © OpenStreetMap'
        }).addTo(map);

        tileLayer.on('tileerror', function (err) {
            if (err && err.tile && !err.tile._triedCarto && err.coords) {
                err.tile._triedCarto = true;
                err.tile.src = `https://basemaps.cartocdn.com/rastertiles/voyager/${err.coords.z}/${err.coords.x}/${err.coords.y}.png`;
            } else if (err && err.tile && !err.tile._triedOsm && err.coords) {
                err.tile._triedOsm = true;
                err.tile.src = `https://tile.openstreetmap.org/${err.coords.z}/${err.coords.x}/${err.coords.y}.png`;
            } else if (err && err.tile && !err.tile._triedProxy && err.coords) {
                err.tile._triedProxy = true;
                err.tile.src = `/api/map/tile/${err.coords.z}/${err.coords.x}/${err.coords.y}.png`;
            }
        });
        return tileLayer;
    }

    window.beNobatMap = {
        maps: {},

        initPicker: function (elOrId, initialLat, initialLng, dotnetHelper, callbackMethod) {
            const el = (typeof elOrId === 'string') ? document.getElementById(elOrId) : elOrId;
            if (!el) return;

            ensureLeaflet().then(L => {
                const mapKey = el.id || ('map_' + Math.random().toString(36).substr(2, 9));
                if (!el.id) el.id = mapKey;

                if (window.beNobatMap.maps[el.id]) {
                    try { window.beNobatMap.maps[el.id].remove(); } catch (e) { }
                    delete window.beNobatMap.maps[el.id];
                }

                const defaultLat = (typeof initialLat === 'number' && !isNaN(initialLat) && initialLat !== 0) ? initialLat : 32.6546;
                const defaultLng = (typeof initialLng === 'number' && !isNaN(initialLng) && initialLng !== 0) ? initialLng : 51.6680;
                const hasInitial = (typeof initialLat === 'number' && typeof initialLng === 'number' && initialLat !== 0 && initialLng !== 0);

                const map = L.map(el, {
                    center: [defaultLat, defaultLng],
                    zoom: hasInitial ? 15 : 12,
                    zoomControl: true
                });

                addTileLayer(map, L);

                const pinIcon = createPinIcon(L);
                let marker = null;

                function updateMarker(lat, lng, notify) {
                    if (!marker) {
                        marker = L.marker([lat, lng], { icon: pinIcon, draggable: true }).addTo(map);
                        marker.on('dragend', function (e) {
                            const pos = e.target.getLatLng();
                            updateMarker(pos.lat, pos.lng, true);
                        });
                    } else {
                        marker.setLatLng([lat, lng]);
                    }

                    if (notify && dotnetHelper && callbackMethod) {
                        dotnetHelper.invokeMethodAsync(callbackMethod, Number(lat.toFixed(6)), Number(lng.toFixed(6)));
                    }
                }

                if (hasInitial) {
                    updateMarker(defaultLat, defaultLng, false);
                }

                map.on('click', function (e) {
                    updateMarker(e.latlng.lat, e.latlng.lng, true);
                });

                // Add "My Location" button control
                const locControl = L.control({ position: 'topright' });
                locControl.onAdd = function () {
                    const btn = L.DomUtil.create('button', 'leaflet-bar leaflet-control leaflet-control-custom');
                    btn.innerHTML = '📍 موقعیت من';
                    btn.style.backgroundColor = '#ffffff';
                    btn.style.border = '1.5px solid #00796b';
                    btn.style.color = '#00796b';
                    btn.style.padding = '6px 12px';
                    btn.style.cursor = 'pointer';
                    btn.style.borderRadius = '6px';
                    btn.style.fontSize = '12px';
                    btn.style.fontWeight = 'bold';
                    btn.style.boxShadow = '0 2px 6px rgba(0,0,0,0.2)';
                    btn.onclick = function (e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (navigator.geolocation) {
                            btn.innerText = 'در حال دریافت...';
                            navigator.geolocation.getCurrentPosition(
                                function (pos) {
                                    btn.innerHTML = '📍 موقعیت من';
                                    const lat = pos.coords.latitude;
                                    const lng = pos.coords.longitude;
                                    map.setView([lat, lng], 16);
                                    updateMarker(lat, lng, true);
                                },
                                function () {
                                    btn.innerHTML = '📍 خطا در GPS';
                                    setTimeout(() => { btn.innerHTML = '📍 موقعیت من'; }, 2000);
                                },
                                { enableHighAccuracy: true, timeout: 10000 }
                            );
                        }
                    };
                    return btn;
                };
                locControl.addTo(map);

                window.beNobatMap.maps[el.id] = map;

                [50, 150, 300, 600, 1200].forEach(delay => {
                    setTimeout(() => {
                        if (window.beNobatMap.maps[el.id]) {
                            window.beNobatMap.maps[el.id].invalidateSize();
                        }
                    }, delay);
                });
            });
        },

        initViewer: function (containerId, lat, lng, title, address) {
            const el = document.getElementById(containerId);
            if (!el) return;

            ensureLeaflet().then(L => {
                if (window.beNobatMap.maps[containerId]) {
                    try { window.beNobatMap.maps[containerId].remove(); } catch (e) { }
                    delete window.beNobatMap.maps[containerId];
                }

                const defaultLat = (typeof lat === 'number' && !isNaN(lat) && lat !== 0) ? lat : 32.6546;
                const defaultLng = (typeof lng === 'number' && !isNaN(lng) && lng !== 0) ? lng : 51.6680;

                const map = L.map(containerId, {
                    center: [defaultLat, defaultLng],
                    zoom: 15,
                    zoomControl: true
                });

                addTileLayer(map, L);

                const pinIcon = createPinIcon(L);
                const marker = L.marker([defaultLat, defaultLng], { icon: pinIcon }).addTo(map);
                if (title || address) {
                    marker.bindPopup(`<b>${title || ''}</b><br/><small>${address || ''}</small>`).openPopup();
                }

                window.beNobatMap.maps[containerId] = map;
                [50, 150, 300, 600, 1200].forEach(delay => {
                    setTimeout(() => {
                        if (window.beNobatMap.maps[containerId]) {
                            window.beNobatMap.maps[containerId].invalidateSize();
                        }
                    }, delay);
                });
            });
        },

        openNavigationModal: function (lat, lng, title, address) {
            let existingModal = document.getElementById('benobat-nav-modal');
            if (existingModal) existingModal.remove();

            const isEn = document.documentElement.lang === 'en';
            const isAr = document.documentElement.lang === 'ar';

            const modalTitle = isEn ? 'Navigate & Directions' : isAr ? 'الملاحة والاتجاهات' : 'مسیریابی و نمایش نشانی';
            const closeText = isEn ? 'Close' : isAr ? 'إغلاق' : 'بستن';
            const copyCoordText = isEn ? 'Copy Coordinates' : isAr ? 'نسخ الإحداثيات' : 'کپی مختصات';
            const copyAddrText = isEn ? 'Copy Address' : isAr ? 'نسخ العنوان' : 'کپی نشانی';
            const copiedText = isEn ? 'Copied!' : isAr ? 'تم النسخ!' : 'کپی شد!';

            const neshanUrl = (lat && lng) ? `https://neshan.org/maps/@${lat},${lng},16z` : `https://neshan.org/maps/search/${encodeURIComponent(address || '')}`;
            const baladUrl = (lat && lng) ? `https://balad.ir/location?latitude=${lat}&longitude=${lng}` : `https://balad.ir/search?query=${encodeURIComponent(address || '')}`;
            const googleUrl = (lat && lng) ? `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}` : `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address || '')}`;
            const wazeUrl = (lat && lng) ? `https://waze.com/ul?ll=${lat},${lng}&navigate=yes` : `https://waze.com/ul?q=${encodeURIComponent(address || '')}&navigate=yes`;
            const osmUrl = (lat && lng) ? `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=16/${lat}/${lng}` : `https://www.openstreetmap.org/search?query=${encodeURIComponent(address || '')}`;

            const modalHtml = `
            <div id="benobat-nav-modal" class="modal-backdrop" style="position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:99999;display:flex;align-items:center;justify-content:center;padding:16px;">
                <div class="nav-modal-card" style="background:var(--card-surface,#ffffff);border-radius:16px;max-width:440px;width:100%;padding:20px;box-shadow:0 12px 36px rgba(0,0,0,0.25);position:relative;animation:fadeIn 0.2s ease-out;" onclick="event.stopPropagation()">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;border-bottom:1px solid rgba(128,128,128,0.2);padding-bottom:10px;">
                        <h3 style="margin:0;font-size:16px;font-weight:bold;color:var(--text-primary,#1a1a1a);">🧭 ${modalTitle}</h3>
                        <button type="button" style="background:none;border:none;font-size:20px;cursor:pointer;color:var(--text-muted,#777);" onclick="document.getElementById('benobat-nav-modal').remove()">×</button>
                    </div>

                    ${title ? `<div style="font-weight:bold;margin-bottom:4px;color:var(--primary,#00796b);">${title}</div>` : ''}
                    ${address ? `<p style="font-size:13px;color:var(--text-secondary,#555);margin-bottom:16px;line-height:1.5;">📍 ${address}</p>` : ''}

                    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:16px;">
                        <a href="${neshanUrl}" target="_blank" rel="noopener noreferrer" style="display:flex;align-items:center;gap:8px;padding:10px 12px;background:rgba(0,121,107,0.08);color:#00796b;border-radius:8px;text-decoration:none;font-weight:600;font-size:13px;border:1px solid rgba(0,121,107,0.2);justify-content:center;">
                            🚗 مسیریاب نشان
                        </a>
                        <a href="${baladUrl}" target="_blank" rel="noopener noreferrer" style="display:flex;align-items:center;gap:8px;padding:10px 12px;background:rgba(33,150,243,0.08);color:#1976d2;border-radius:8px;text-decoration:none;font-weight:600;font-size:13px;border:1px solid rgba(33,150,243,0.2);justify-content:center;">
                            🗺️ مسیریاب بلد
                        </a>
                        <a href="${googleUrl}" target="_blank" rel="noopener noreferrer" style="display:flex;align-items:center;gap:8px;padding:10px 12px;background:rgba(234,67,53,0.08);color:#d32f2f;border-radius:8px;text-decoration:none;font-weight:600;font-size:13px;border:1px solid rgba(234,67,53,0.2);justify-content:center;">
                            🧭 Google Maps
                        </a>
                        <a href="${wazeUrl}" target="_blank" rel="noopener noreferrer" style="display:flex;align-items:center;gap:8px;padding:10px 12px;background:rgba(51,204,255,0.08);color:#0288d1;border-radius:8px;text-decoration:none;font-weight:600;font-size:13px;border:1px solid rgba(51,204,255,0.2);justify-content:center;">
                            🚙 Waze
                        </a>
                    </div>

                    <a href="${osmUrl}" target="_blank" rel="noopener noreferrer" style="display:block;text-align:center;padding:8px;background:rgba(128,128,128,0.08);color:var(--text-primary,#333);border-radius:8px;text-decoration:none;font-size:12px;margin-bottom:12px;">
                        🌐 مشاهده در OpenStreetMap
                    </a>

                    <div style="display:flex;gap:8px;">
                        ${(lat && lng) ? `
                        <button type="button" id="btn-copy-coords" style="flex:1;padding:8px;background:none;border:1px solid var(--border,#ccc);border-radius:6px;font-size:12px;cursor:pointer;" onclick="navigator.clipboard.writeText('${lat},${lng}').then(() => { this.innerText = '${copiedText}'; setTimeout(() => this.innerText = '${copyCoordText}', 2000); })">
                            📋 ${copyCoordText}
                        </button>
                        ` : ''}
                        ${address ? `
                        <button type="button" id="btn-copy-addr" style="flex:1;padding:8px;background:none;border:1px solid var(--border,#ccc);border-radius:6px;font-size:12px;cursor:pointer;" onclick="navigator.clipboard.writeText('${address.replace(/'/g, "\\'")}').then(() => { this.innerText = '${copiedText}'; setTimeout(() => this.innerText = '${copyAddrText}', 2000); })">
                            📋 ${copyAddrText}
                        </button>
                        ` : ''}
                    </div>
                </div>
            </div>`;

            document.body.insertAdjacentHTML('beforeend', modalHtml);
            document.getElementById('benobat-nav-modal').onclick = function () { this.remove(); };
        },

        showMapModal: function (lat, lng, title, address) {
            let existingModal = document.getElementById('benobat-map-preview-modal');
            if (existingModal) existingModal.remove();

            const isEn = document.documentElement.lang === 'en';
            const isAr = document.documentElement.lang === 'ar';
            const modalTitle = isEn ? 'Location on Map' : isAr ? 'الموقع على الخريطة' : 'موقعیت روی نقشه';
            const navBtnText = isEn ? 'Navigate' : isAr ? 'ملاحة' : 'مسیریابی';

            const modalHtml = `
            <div id="benobat-map-preview-modal" class="modal-backdrop" style="position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:99999;display:flex;align-items:center;justify-content:center;padding:16px;">
                <div style="background:var(--card-surface,#ffffff);border-radius:16px;max-width:600px;width:100%;padding:20px;box-shadow:0 12px 36px rgba(0,0,0,0.25);position:relative;" onclick="event.stopPropagation()">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <h3 style="margin:0;font-size:16px;font-weight:bold;color:var(--text-primary,#1a1a1a);">📍 ${modalTitle}</h3>
                        <button type="button" style="background:none;border:none;font-size:20px;cursor:pointer;color:var(--text-muted,#777);" onclick="document.getElementById('benobat-map-preview-modal').remove()">×</button>
                    </div>
                    ${title ? `<div style="font-weight:bold;margin-bottom:4px;color:var(--primary,#00796b);">${title}</div>` : ''}
                    ${address ? `<p style="font-size:13px;color:var(--text-secondary,#555);margin-bottom:10px;">${address}</p>` : ''}
                    <div id="modal-map-container" style="height:320px;width:100%;border-radius:10px;margin-bottom:12px;background:#e2e8f0;position:relative;"></div>
                    <div style="display:flex;justify-content:flex-end;gap:8px;">
                        <button type="button" class="action-secondary" style="padding:8px 16px;border-radius:8px;cursor:pointer;" onclick="document.getElementById('benobat-map-preview-modal').remove()">بستن</button>
                        <button type="button" class="primary-button" style="padding:8px 16px;border-radius:8px;cursor:pointer;background:var(--primary,#00796b);color:#fff;" onclick="window.beNobatMap.openNavigationModal(${lat}, ${lng}, '${(title || '').replace(/'/g, "\\'")}', '${(address || '').replace(/'/g, "\\'")}'); document.getElementById('benobat-map-preview-modal').remove();">🧭 ${navBtnText}</button>
                    </div>
                </div>
            </div>`;

            document.body.insertAdjacentHTML('beforeend', modalHtml);
            document.getElementById('benobat-map-preview-modal').onclick = function () { this.remove(); };

            window.beNobatMap.initViewer('modal-map-container', lat, lng, title, address);
        }
    };
})();
