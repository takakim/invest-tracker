import React from 'react';
import { Layout } from './components';
import { AppRoutes } from './routing/routes';

export default function App() {
  return (
    <Layout>
      <AppRoutes />
    </Layout>
  );
}
