import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const listAllDuration = new Trend('list_all_invoices_duration', true);

export const options = {
    scenarios: {
        list_invoices: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '2m',
            exec: 'listInvoices',
        },
        submit_single: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 5,
            maxDuration: '1m',
            exec: 'submitSingle',
            startTime: '10s',
        },
        bulk_check_status: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '1m',
            exec: 'bulkCheckStatus',
            startTime: '20s',
        },
    },
    thresholds: {
        'list_all_invoices_duration': ['p(95)<2000'],
        'http_req_duration{scenario:submit_single}': ['p(95)<5000'],
        'http_req_duration{scenario:bulk_check_status}': ['p(95)<30000'],
    },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';
const COMPANY_ID = __ENV.COMPANY_ID || '00000000-0000-0000-0000-000000000001';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || '';
const RUN_ID = __ENV.RUN_ID || Date.now().toString();

const params = {
    headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${AUTH_TOKEN}`,
    },
};

function buildInvoicePayload(invoiceNumber) {
    return {
        invoiceNumber: invoiceNumber,
        documentType: 'i',
        documentTypeVersion: '1.0',
        issueDatetime: new Date().toISOString(),
        currency: 'EGP',
        taxpayerActivityCode: '4620',
        sellerData: { type: 'B', name: 'Perf Test Corp' },
        buyerData: { type: 'P', name: 'Buyer' },
        lines: [
            {
                lineNumber: 1,
                itemCode: 'PERF-001',
                itemType: 'GS1',
                description: 'Performance test item',
                quantity: 1,
                unitValue: {
                    currencySold: 'EGP',
                    amountEGP: '100.00000',
                    amountSold: '100.00000',
                    currencyExchangeRate: '1.00000',
                },
                total: '100.00000',
                taxes: [
                    { taxType: 'V1', taxAmount: '14.00000' },
                ],
            },
        ],
        totalSalesAmount: '100.00000',
        totalDiscountAmount: '0.00000',
        netAmount: '100.00000',
        totalAmount: '114.00000',
    };
}

export function setup() {
    const docIds = [];
    for (let i = 0; i < 210; i++) {
        const invoiceNumber = `PERF-SETUP-${RUN_ID}-${i}`;
        const payload = buildInvoicePayload(invoiceNumber);

        const res = http.post(
            `${BASE_URL}/api/companies/${COMPANY_ID}/eta/invoices`,
            JSON.stringify(payload),
            params,
        );

        if (res.status !== 201 && res.status !== 200) {
            fail(`Setup: create invoice ${i} (${invoiceNumber}) failed with status ${res.status}: ${res.body}`);
        }

        try {
            const body = JSON.parse(res.body);
            const id = body.id || (body.body && body.body.id);
            if (id) {
                docIds.push(id);
            } else {
                fail(`Setup: no ID returned for invoice ${i} (${invoiceNumber})`);
            }
        } catch (e) {
            fail(`Setup: parse error for invoice ${i}: ${e}`);
        }
    }

    if (docIds.length < 200) {
        fail(`Setup: only ${docIds.length} invoices created, need at least 200 for bulk scenario`);
    }

    console.info(`Setup: created ${docIds.length} invoices (run ${RUN_ID})`);
    return { docIds: docIds };
}

export function listInvoices() {
    const maxPageSize = 500;
    let page = 0;
    let totalFetched = 0;
    const startMs = Date.now();

    while (true) {
        const url = `${BASE_URL}/api/companies/${COMPANY_ID}/eta/invoices?page=${page}&size=${maxPageSize}`;
        const res = http.get(url, params);
        const ok = check(res, {
            'list status 200': (r) => r.status === 200,
        });
        if (!ok) {
            fail(`listInvoices page ${page} returned ${res.status}`);
        }

        const body = JSON.parse(res.body);
        const items = body.items || [];
        totalFetched += items.length;

        const totalElements = body.totalElements || 0;
        if (totalFetched >= totalElements || items.length === 0) {
            break;
        }
        page++;
    }

    const elapsedMs = Date.now() - startMs;
    listAllDuration.add(elapsedMs);

    sleep(0.1);
}

export function submitSingle() {
    const vuId = exec.vu.idInTest;
    const iter = exec.vu.iterationInInstance;
    const invoiceNumber = `PERF-${RUN_ID}-${vuId}-${iter}`;
    const payload = buildInvoicePayload(invoiceNumber);

    const createUrl = `${BASE_URL}/api/companies/${COMPANY_ID}/eta/invoices`;
    const createRes = http.post(createUrl, JSON.stringify(payload), params);
    const createOk = check(createRes, {
        'create status 201 or 200': (r) => r.status === 201 || r.status === 200,
    });
    if (!createOk) {
        fail(`submitSingle: create ${invoiceNumber} returned ${createRes.status}: ${createRes.body}`);
    }

    const body = JSON.parse(createRes.body);
    const docId = body.id || (body.body && body.body.id);
    if (docId) {
        const submitUrl = `${BASE_URL}/api/companies/${COMPANY_ID}/eta/invoices/${docId}/submit`;
        const submitRes = http.post(submitUrl, '{}', params);
        const submitOk = check(submitRes, {
            'submit completed': (r) => r.status === 200 || r.status === 202,
        });
        if (!submitOk) {
            fail(`submitSingle: submit ${docId} returned ${submitRes.status}: ${submitRes.body}`);
        }
    }
    sleep(0.5);
}

export function bulkCheckStatus(data) {
    const docIds = (data && data.docIds) ? data.docIds.slice(0, 200) : [];
    if (docIds.length < 200) {
        fail(`bulkCheckStatus: only ${docIds.length} doc IDs available — need 200`);
    }

    const url = `${BASE_URL}/api/companies/${COMPANY_ID}/eta/invoices/check-status`;
    const res = http.post(url, JSON.stringify({ documentIds: docIds }), params);
    const ok = check(res, {
        'bulk check completed': (r) => r.status === 200 || r.status === 207,
    });
    if (!ok) {
        fail(`bulkCheckStatus returned ${res.status}: ${res.body}`);
    }
}

export function teardown(data) {
    console.info(`Teardown: ${data.docIds.length} invoices created during setup (run ${RUN_ID})`);
}
