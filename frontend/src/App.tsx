import { useState } from 'react';
import { Link, Route, Routes, useParams } from 'react-router-dom';
import { Alert, AppBar, Box, Button, Container, Drawer, List, ListItemButton, ListItemText, Paper, Stack, TextField, Toolbar, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './api';

function Layout({ children }: { children: React.ReactNode }) {
  return <Box sx={{ display: 'flex', minHeight: '100vh' }}>
    <Drawer variant="permanent"><Toolbar /><List sx={{ width: 240 }}><ListItemButton component={Link} to="/"><ListItemText primary="Portfolios" /></ListItemButton></List></Drawer>
    <Box sx={{ flex: 1 }}><AppBar position="static"><Toolbar><Typography variant="h6">Invest Tracker</Typography></Toolbar></AppBar><Container sx={{ py: 4 }}>{children}</Container></Box>
  </Box>;
}

function Portfolios() {
  const query = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios });
  if (query.isPending) return <Typography>Loading portfolios…</Typography>;
  if (query.isError) return <Alert severity="error">{query.error.message}</Alert>;
  return <Stack spacing={2}><Stack direction="row" sx={{ justifyContent: 'space-between' }}><Typography variant="h4">Portfolios</Typography><Button component={Link} to="/portfolios/new" variant="contained">New portfolio</Button></Stack>{query.data.length === 0 ? <Paper sx={{ p: 3 }}>No portfolios yet.</Paper> : <List>{query.data.map(p => <ListItemButton key={p.id} component={Link} to={`/portfolios/${p.id}`}><ListItemText primary={p.name} secondary={`${p.baseCurrency} · ${p.costBasisMethod} · ${p.returnMethod}`} /></ListItemButton>)}</List>}</Stack>;
}

function PortfolioDetail() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['accounts', id], queryFn: () => api.accounts(id), enabled: Boolean(id) });
  return <Stack spacing={2}><Button component={Link} to="/">← Portfolios</Button><Typography variant="h4">Portfolio</Typography><Button component={Link} to={`/portfolios/${id}/accounts/new`} variant="contained">New account</Button>{query.isPending ? <Typography>Loading accounts…</Typography> : query.isError ? <Alert severity="error">{query.error.message}</Alert> : query.data.length === 0 ? <Paper sx={{ p: 3 }}>No accounts yet.</Paper> : <List>{query.data.map(a => <ListItemButton key={a.id}><ListItemText primary={a.name} secondary={`${a.brokerName} · ${a.accountCurrency}`} /></ListItemButton>)}</List>}</Stack>;
}

function NewPortfolio() {
  const client = useQueryClient();
  const [name, setName] = useState('');
  const [currency, setCurrency] = useState('GBP');
  const mutation = useMutation({ mutationFn: () => api.createPortfolio({ name, baseCurrency: currency, costBasisMethod: 'FIFO', returnMethod: 'XIRR' }), onSuccess: () => client.invalidateQueries({ queryKey: ['portfolios'] }) });
  return <Stack spacing={2} sx={{ maxWidth: 520 }}><Typography variant="h4">New portfolio</Typography><TextField label="Name" value={name} onChange={e => setName(e.target.value)} /><TextField label="Base currency" value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} slotProps={{ htmlInput: { maxLength: 3 } }} /><Button variant="contained" disabled={!name || mutation.isPending} onClick={() => mutation.mutate()}>Create</Button>{mutation.isError && <Alert severity="error">{mutation.error.message}</Alert>}</Stack>;
}

function NewAccount() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const [name, setName] = useState(''); const [broker, setBroker] = useState(''); const [currency, setCurrency] = useState('GBP');
  const mutation = useMutation({ mutationFn: () => api.createAccount(id, { name, brokerName: broker, accountCurrency: currency }), onSuccess: () => client.invalidateQueries({ queryKey: ['accounts', id] }) });
  return <Stack spacing={2} sx={{ maxWidth: 520 }}><Typography variant="h4">New account</Typography><TextField label="Account name" value={name} onChange={e => setName(e.target.value)} /><TextField label="Broker / custodian" value={broker} onChange={e => setBroker(e.target.value)} /><TextField label="Currency" value={currency} onChange={e => setCurrency(e.target.value.toUpperCase())} slotProps={{ htmlInput: { maxLength: 3 } }} /><Button variant="contained" disabled={!name || !broker || mutation.isPending} onClick={() => mutation.mutate()}>Create</Button>{mutation.isError && <Alert severity="error">{mutation.error.message}</Alert>}</Stack>;
}

export default function App() { return <Layout><Routes><Route path="/" element={<Portfolios />} /><Route path="/portfolios/new" element={<NewPortfolio />} /><Route path="/portfolios/:id" element={<PortfolioDetail />} /><Route path="/portfolios/:id/accounts/new" element={<NewAccount />} /></Routes></Layout>; }
